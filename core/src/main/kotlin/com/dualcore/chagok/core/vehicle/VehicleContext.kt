package com.dualcore.chagok.core.vehicle

import kotlinx.serialization.Serializable

/**
 * 데이터 계약 v1 (2026-11-03). 연동 포트는 연동 담당 주도이며 SDK 결과표에 맞춰 조정한다.
 * SDK를 거친 값은 에뮬레이터여도 VEHICLE, 앱이 만든 가짜 값만 SIMULATED.
 */
enum class DataSource { VEHICLE, USER, SIMULATED }
enum class Availability { AVAILABLE, STALE, UNAVAILABLE }

/** 값마다가 아니라 앱 전체에 하나. 영상·화면에서 에뮬레이터 검증을 자동으로 드러낸다. */
enum class RuntimeEnvironment { EMULATOR, VEHICLE, UNKNOWN }

enum class VehicleSignal { ODOMETER_KM, BATTERY_PERCENT, FUEL_PERCENT, GEAR, SPEED_KMH }

/** 미수신을 0으로 채우지 않는다. UNAVAILABLE이면 value가 비어 있어야 한다. */
@Serializable
data class VehicleReading(
    val signal: VehicleSignal,
    val value: String?,
    val unit: String? = null,
    val source: DataSource,
    val observedAt: Long?,
    val availability: Availability,
) {
    init { require((availability == Availability.UNAVAILABLE) == (value == null)) }
}

@Serializable
data class VehicleSnapshot(
    val readings: List<VehicleReading>,
    val runtime: RuntimeEnvironment,
) {
    fun reading(signal: VehicleSignal): VehicleReading? = readings.firstOrNull { it.signal == signal }
    val isSimulated: Boolean get() = readings.any { it.source == DataSource.SIMULATED }
}

sealed interface VehicleEvent {
    data class ParkingChanged(val parked: Boolean?) : VehicleEvent
    data class DestinationChanged(val label: String?) : VehicleEvent
    data class Arrived(val label: String?) : VehicleEvent
    data class AvailabilityChanged(val signal: VehicleSignal, val availability: Availability) : VehicleEvent
}

interface VehicleContextSource {
    val runtime: RuntimeEnvironment
    fun capabilities(): Set<VehicleSignal>
    fun currentSnapshot(): VehicleSnapshot

    /** null은 미확인. 앱은 주차가 확인됐을 때만 입력을 연다. */
    fun isParked(): Boolean?

    /** 지금 내비에 설정된 목적지 이름. 미지원이거나 경로가 없으면 null. 이후 변경은 DestinationChanged로 받는다. */
    fun currentDestination(): String? = null

    fun subscribe(listener: (VehicleEvent) -> Unit): AutoCloseable
}
