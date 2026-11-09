package io.github.beomhui.drivecompanion.core.vehicle

import java.util.concurrent.CopyOnWriteArrayList

/** 실제 어댑터가 오기 전 화면 개발용. 모든 값은 SIMULATED로 표시되고 실행 환경은 알 수 없다. */
class SimulatedVehicleContextSource(
    private val clock: () -> Long = System::currentTimeMillis,
    private var parked: Boolean? = true,
) : VehicleContextSource {
    private val listeners = CopyOnWriteArrayList<(VehicleEvent) -> Unit>()

    override val runtime = RuntimeEnvironment.UNKNOWN

    override fun capabilities() = setOf(VehicleSignal.ODOMETER_KM, VehicleSignal.BATTERY_PERCENT, VehicleSignal.GEAR)

    override fun currentSnapshot(): VehicleSnapshot {
        val now = clock()
        fun simulated(signal: VehicleSignal, value: String, unit: String? = null) =
            VehicleReading(signal, value, unit, DataSource.SIMULATED, now, Availability.AVAILABLE)
        return VehicleSnapshot(
            readings = listOf(
                simulated(VehicleSignal.ODOMETER_KM, "12480", "km"),
                simulated(VehicleSignal.BATTERY_PERCENT, "76", "%"),
                simulated(VehicleSignal.GEAR, if (parked == true) "P" else "D"),
                VehicleReading(VehicleSignal.FUEL_PERCENT, null, null, DataSource.SIMULATED, null, Availability.UNAVAILABLE),
            ),
            runtime = runtime,
        )
    }

    override fun isParked(): Boolean? = parked

    fun setParked(value: Boolean?) {
        if (parked == value) return
        parked = value
        listeners.forEach { it(VehicleEvent.ParkingChanged(value)) }
    }

    private var destination: String? = null

    override fun currentDestination(): String? = destination

    fun setDestination(label: String?) {
        if (destination == label) return
        destination = label
        listeners.forEach { it(VehicleEvent.DestinationChanged(label)) }
    }

    /** 내비 도착 신호만 보낸다. 주차는 따로 바꾼다. 주차 ≠ 도착을 시연에서 나눠 보여주기 위해서다. */
    fun arrive() {
        val label = destination ?: return
        listeners.forEach { it(VehicleEvent.Arrived(label)) }
    }

    override fun subscribe(listener: (VehicleEvent) -> Unit): AutoCloseable {
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }
}
