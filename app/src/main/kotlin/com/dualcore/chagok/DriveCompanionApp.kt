package com.dualcore.chagok

import android.app.Application
import android.content.Context
import com.dualcore.chagok.core.handover.Fleet
import com.dualcore.chagok.core.handover.HandoverService
import com.dualcore.chagok.core.speech.SimulatedSpeechPort
import com.dualcore.chagok.core.speech.SpeechPort
import com.dualcore.chagok.core.vehicle.SimulatedVehicleContextSource
import com.dualcore.chagok.core.vehicle.VehicleContextSource
import com.dualcore.chagok.data.local.FileHandoverStore
import com.dualcore.chagok.integration.PleosSpeechPort
import com.dualcore.chagok.integration.PleosVehicleContextSource
import java.io.File

class DriveCompanionApp : Application() {
    val container by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val fleet = Fleet.SAMPLE

    /** Pleos 차량 서비스가 있으면 실제 SDK 값, 없으면 SIMULATED로 표시되는 시뮬레이션을 쓴다. */
    val vehicle: VehicleContextSource =
        if (PleosVehicleContextSource.isAvailable(context)) PleosVehicleContextSource(context).also { it.start() }
        else SimulatedVehicleContextSource()

    val speech: SpeechPort =
        if (PleosVehicleContextSource.isAvailable(context)) PleosSpeechPort(context) else SimulatedSpeechPort()

    val store = FileHandoverStore(File(context.filesDir, "handover.json"))

    /** 첫 접근 때 파일을 읽으므로 메인 스레드에서 처음 부르지 않는다. */
    val service: HandoverService by lazy { HandoverService(fleet, store) }
}
