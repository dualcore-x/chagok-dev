package com.dualcore.chagok.integration

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.EvBatteryStateOfChargeListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.dualcore.chagok.core.vehicle.Availability
import com.dualcore.chagok.core.vehicle.DataSource
import com.dualcore.chagok.core.vehicle.RuntimeEnvironment
import com.dualcore.chagok.core.vehicle.VehicleContextSource
import com.dualcore.chagok.core.vehicle.VehicleEvent
import com.dualcore.chagok.core.vehicle.VehicleReading
import com.dualcore.chagok.core.vehicle.VehicleSignal
import com.dualcore.chagok.core.vehicle.VehicleSnapshot
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.roundToLong

/**
 * Pleos Vehicle·NaviHelper SDK 어댑터. SDK 값은 콜백으로 늦게 오므로 마지막 값을 캐시하고, currentSnapshot()은 캐시만 읽는다.
 * 지원 범위는 docs/sdk-checks.md의 에뮬레이터 실측을 따른다. 받지 못한 값은 UNAVAILABLE로 남기고 0으로 채우지 않는다.
 */
class PleosVehicleContextSource(
    context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleContextSource {
    private val vehicle = Vehicle(context)
    private val navi = NaviHelper(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(VehicleEvent) -> Unit>()
    private val readings = ConcurrentHashMap<VehicleSignal, VehicleReading>()

    @Volatile private var parked: Boolean? = null
    @Volatile private var destination: String? = null

    override val runtime = if (Build.HARDWARE == "ranchu") RuntimeEnvironment.EMULATOR else RuntimeEnvironment.VEHICLE

    override fun capabilities() = readings.values.filter { it.availability == Availability.AVAILABLE }.map { it.signal }.toSet()

    override fun currentSnapshot() = VehicleSnapshot(
        readings = SIGNALS.map { readings[it] ?: unavailable(it) },
        runtime = runtime,
    )

    override fun isParked(): Boolean? = parked

    override fun currentDestination(): String? = destination

    override fun subscribe(listener: (VehicleEvent) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    fun start() {
        vehicle.initialize()
        navi.initialize(
            onConnected = {
                navi.addListener(naviListener)
                pollDestination()
            },
            onFailed = { Log.w(TAG, "NaviHelper connection failed", it) },
        )
        // Vehicle.initialize()는 연결 완료 콜백이 없다. 바인딩 전 호출이 실패하면 재시도한다.
        main.postDelayed({ connect(attempt = 1) }, CONNECT_DELAY_MS)
    }

    // 내비 화면에서 직접 시작한 경로는 onRouteStarted·onDestinationChanged가 오지 않는다(에뮬레이터 실측).
    private fun pollDestination() {
        navi.getDestinationInfo()
        main.postDelayed(::pollDestination, DESTINATION_POLL_MS)
    }

    private fun connect(attempt: Int) {
        vehicle.getOdometer().getCurrentDistanceDriven(
            onComplete = { km ->
                onOdometer(km)
                vehicle.getOdometer().registerDistanceDriven(odometerListener)
                vehicle.getGear().registerGearSelection(gearListener)
                vehicle.getEvBattery().registerEvBatteryStateOfCharge(socListener)
                vehicle.getGear().getCurrentGearSelectionState(::onGear, ::onFailed)
                vehicle.getEvBattery().getEvBatteryStateOfCharge(::onSoc, ::onFailed)
                vehicle.getFuel().getFuelPercent({ percent -> put(VehicleSignal.FUEL_PERCENT, percent?.toString(), "%") }, ::onFailed)
            },
            onFailed = { e ->
                if (attempt < MAX_CONNECT_ATTEMPTS) main.postDelayed({ connect(attempt + 1) }, CONNECT_DELAY_MS)
                else Log.w(TAG, "Vehicle service unavailable after $attempt attempts", e)
            },
        )
    }

    private fun onOdometer(km: Float?) = put(VehicleSignal.ODOMETER_KM, km?.roundToLong()?.toString(), "km")

    private fun onSoc(percent: Int?) = put(VehicleSignal.BATTERY_PERCENT, percent?.toString(), "%")

    private fun onGear(gear: VehicleGear?) {
        val label = when (gear) {
            VehicleGear.GEAR_PARK -> "P"
            VehicleGear.GEAR_REVERSE -> "R"
            VehicleGear.GEAR_NEUTRAL -> "N"
            null, VehicleGear.GEAR_UNKNOWN -> null
            else -> "D"
        }
        put(VehicleSignal.GEAR, label, null)
        val nowParked = label?.let { it == "P" }
        if (nowParked != parked) {
            parked = nowParked
            emit(VehicleEvent.ParkingChanged(nowParked))
        }
    }

    // 미지원 속성은 IllegalArgumentException, 권한 없음은 SecurityException으로 온다. 어느 쪽이든 UNAVAILABLE로 둔다.
    private fun onFailed(e: Exception) {
        Log.w(TAG, "Vehicle read failed", e)
    }

    private fun put(signal: VehicleSignal, value: String?, unit: String?) {
        val reading = if (value == null) unavailable(signal)
        else VehicleReading(signal, value, unit, DataSource.VEHICLE, clock(), Availability.AVAILABLE)
        val before = readings.put(signal, reading)?.availability ?: Availability.UNAVAILABLE
        if (before != reading.availability) emit(VehicleEvent.AvailabilityChanged(signal, reading.availability))
    }

    private fun unavailable(signal: VehicleSignal) =
        VehicleReading(signal, null, null, DataSource.VEHICLE, null, Availability.UNAVAILABLE)

    private fun emit(event: VehicleEvent) = listeners.forEach { it(event) }

    private val odometerListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) = onOdometer(value)
        override fun onFailed(e: Exception) = this@PleosVehicleContextSource.onFailed(e)
    }

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) = this@PleosVehicleContextSource.onFailed(e)
    }

    private val socListener = object : EvBatteryStateOfChargeListener {
        override fun onEvBatteryStateOfChargeUpdated(value: Int?) = onSoc(value)
        override fun onFailed(e: Exception) = this@PleosVehicleContextSource.onFailed(e)
    }

    private val naviListener = object : NaviHelperEventListener {
        // 목적지가 없으면 위경도 0, 이름 공백인 빈 DestinationInfo가 온다.
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            val label = destinationInfo.name.ifBlank { null }
            if (label == destination) return
            destination = label
            emit(VehicleEvent.DestinationChanged(label))
        }

        override fun onDestinationChanged() = navi.getDestinationInfo()

        // requestRoute로 목적지를 정하면 onDestinationChanged 없이 이 콜백만 온다(에뮬레이터 실측).
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = navi.getDestinationInfo()

        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) =
            emit(VehicleEvent.Arrived(destinationArrivedInfo.name.ifBlank { destination }))

        override fun onError(errorCode: NaviErrorCode) {
            Log.w(TAG, "NaviHelper error $errorCode")
        }
    }

    companion object {
        private const val TAG = "PleosVehicle"
        private const val CONNECT_DELAY_MS = 1_000L
        private const val MAX_CONNECT_ATTEMPTS = 10
        private const val DESTINATION_POLL_MS = 10_000L
        private val SIGNALS = listOf(VehicleSignal.ODOMETER_KM, VehicleSignal.BATTERY_PERCENT, VehicleSignal.FUEL_PERCENT, VehicleSignal.GEAR)

        /** Pleos 차량 서비스가 깔린 기기에서만 실제 어댑터를 쓴다. 일반 에뮬레이터는 시뮬레이션으로 개발한다. */
        fun isAvailable(context: Context): Boolean = try {
            context.packageManager.getPackageInfo("ai.pleos.playground.service.vehicle", 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }
}
