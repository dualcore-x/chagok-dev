package dev.handover.probe

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import android.app.Activity
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView

/**
 * 개발·시연용 확인 앱. adb로 명령을 보내고 결과는 logcat 태그 PROBE로 본다.
 *   adb shell am start -S -n dev.handover.probe/.MainActivity --es cmd <명령> [옵션]
 * 명령: values, loc, route(--es lat --es lon --es name), cancel, focus(--es type gain|transient)
 */
class MainActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var out: TextView
    private lateinit var vehicle: Vehicle
    private lateinit var navi: NaviHelper
    private var naviReady = false
    private val pending = ArrayDeque<Intent>()

    private fun log(msg: String) {
        Log.i("PROBE", msg)
        main.post { out.append(msg + "\n") }
    }

    private fun fail(what: String) = { e: Exception -> log("$what FAILED ${e.javaClass.simpleName}: ${e.message}") }

    private val naviListener = object : NaviHelperEventListener {
        override fun onDestinationInfo(destinationInfo: DestinationInfo) =
            log("destination: ${destinationInfo.name.ifBlank { "(none)" }} ${destinationInfo.latitude},${destinationInfo.longitude}")
        override fun onDestinationChanged() = log("destination changed")
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = log("ARRIVED: $destinationArrivedInfo")
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("route started: $routeStartInfo")
        override fun onRouteCancelled() = log("route cancelled")
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = log("location: $currentLocationInfo")
        override fun onError(errorCode: NaviErrorCode) = log("navi error: $errorCode")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        out = TextView(this).apply { textSize = 18f }
        setContentView(ScrollView(this).apply { addView(out) })
        vehicle = Vehicle(this).also { it.initialize() }
        navi = NaviHelper(applicationContext)
        navi.initialize(
            // 연결 콜백이 어느 스레드에서 오는지 보장되지 않아, 상태는 메인 스레드에서만 바꾼다.
            onConnected = {
                main.post {
                    log("navi connected")
                    navi.addListener(naviListener)
                    naviReady = true
                    while (pending.isNotEmpty()) handle(pending.removeFirst())
                }
            },
            onFailed = {
                log("navi init FAILED: $it")
                main.post { pending.forEach { i -> log("dropped: ${i.getStringExtra("cmd")}") }; pending.clear() }
            },
        )
        // Vehicle.initialize()는 연결 완료 콜백이 없어 잠시 기다린다.
        // 화면이 다시 만들어질 때(구성 변경) 같은 명령을 또 실행하지 않는다.
        if (savedInstanceState == null) main.postDelayed({ submit(intent) }, 1500)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        submit(intent)
    }

    /** focus는 내비와 무관해서 바로 처리하고, 나머지는 내비 연결 뒤에 순서대로 처리한다. */
    private fun submit(intent: Intent) {
        if (intent.getStringExtra("cmd") == "focus" || naviReady) handle(intent) else pending.add(intent)
    }

    private fun handle(intent: Intent) {
        val cmd = intent.getStringExtra("cmd") ?: "values"
        log("cmd: $cmd")
        when (cmd) {
            "values" -> {
                vehicle.getOdometer().getCurrentDistanceDriven({ log("odometer: $it") }, fail("odometer"))
                vehicle.getGear().getCurrentGearSelectionState({ log("gear: $it") }, fail("gear"))
                vehicle.getEvBattery().getEvBatteryStateOfCharge({ log("ev soc: $it") }, fail("ev soc"))
                vehicle.getFuel().getFuelPercent({ log("fuel: $it") }, fail("fuel"))
                vehicle.getSafety().getTelltalesInfo({ log("telltales: $it") }, fail("telltales"))
                navi.getDestinationInfo()
            }
            "loc" -> navi.getCurrentLocationInfo()
            "route" -> {
                val lat = intent.getStringExtra("lat")?.toDoubleOrNull()
                val lon = intent.getStringExtra("lon")?.toDoubleOrNull()
                if (lat == null || lon == null) return log("route needs --es lat <num> --es lon <num>")
                navi.requestRoute(
                    RouteInfo(
                        latitude = lat,
                        longitude = lon,
                        poiName = intent.getStringExtra("name") ?: "probe",
                        poiId = "",
                        address = "",
                        poiSubId = "",
                    ),
                )
            }
            "cancel" -> navi.cancelRoute()
            "focus" -> requestFocus(intent.getStringExtra("type") ?: "gain")
            else -> log("unknown cmd: $cmd")
        }
    }

    /** 다른 앱이 오디오 포커스를 가져가는 상황(음악, Gleo)을 흉내 낸다. 확인용 앱을 끄면 반납된다. */
    private fun requestFocus(type: String) {
        val transient = type == "transient"
        val request = AudioFocusRequest.Builder(if (transient) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT else AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (transient) AudioAttributes.USAGE_ASSISTANT else AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
            .setOnAudioFocusChangeListener { log("probe focus change $it") }
            .build()
        log("probe focus $type result=${getSystemService(AudioManager::class.java).requestAudioFocus(request)}")
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        navi.removeListener(naviListener)
        navi.release()
        vehicle.release()
        super.onDestroy()
    }
}
