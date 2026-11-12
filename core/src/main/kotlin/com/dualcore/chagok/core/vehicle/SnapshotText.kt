package com.dualcore.chagok.core.vehicle

/**
 * 인계와 함께 남는 차량 상태 한 줄. 출처와 상태를 값마다 드러낸다(Q05).
 * 주행거리·배터리는 못 받아도 "미수신"으로 남기고 0으로 채우지 않는다. 연료는 받은 차에서만 보여준다.
 * 이전 값은 "(이전 값)", 직접 입력은 "(직접 입력)", 앱이 만든 값이 하나라도 있으면 끝에 "시뮬레이션"을 붙인다.
 */
fun describeSnapshot(snapshot: VehicleSnapshot): String {
    val shown = listOf(VehicleSignal.ODOMETER_KM, VehicleSignal.BATTERY_PERCENT, VehicleSignal.FUEL_PERCENT)
        .mapNotNull { signal -> snapshot.reading(signal)?.let { signal to it } }
        .filter { (signal, reading) -> signal != VehicleSignal.FUEL_PERCENT || reading.availability != Availability.UNAVAILABLE }
    if (shown.none { it.second.availability != Availability.UNAVAILABLE }) return "차량 값 미수신"
    val parts = shown.map { (signal, reading) ->
        val label = when (signal) {
            VehicleSignal.ODOMETER_KM -> "주행거리"
            VehicleSignal.BATTERY_PERCENT -> "배터리"
            else -> "연료"
        }
        val value = reading.value ?: return@map "$label 미수신"
        val number = value.toLongOrNull()?.let { "%,d".format(it) } ?: value
        val unit = if (signal == VehicleSignal.ODOMETER_KM) "km" else "%"
        val notes = listOfNotNull(
            "이전 값".takeIf { reading.availability == Availability.STALE },
            "직접 입력".takeIf { reading.source == DataSource.USER },
        )
        "$label $number$unit" + if (notes.isEmpty()) "" else notes.joinToString(", ", "(", ")")
    }
    return parts.joinToString(" · ") + if (snapshot.isSimulated) " · 시뮬레이션" else ""
}
