package io.github.beomhui.drivecompanion.core

import io.github.beomhui.drivecompanion.core.vehicle.*
import org.junit.Assert.assertEquals
import org.junit.Test

class SnapshotTextTest {
    private fun reading(signal: VehicleSignal, value: String?, source: DataSource = DataSource.VEHICLE, stale: Boolean = false) =
        VehicleReading(
            signal, value, null, source, if (value == null) null else 1L,
            when {
                value == null -> Availability.UNAVAILABLE
                stale -> Availability.STALE
                else -> Availability.AVAILABLE
            },
        )

    private fun snapshot(vararg readings: VehicleReading) = VehicleSnapshot(readings.toList(), RuntimeEnvironment.EMULATOR)

    @Test fun `vehicle values read plainly`() {
        assertEquals(
            "주행거리 30,000km · 배터리 80%",
            describeSnapshot(snapshot(
                reading(VehicleSignal.ODOMETER_KM, "30000"), reading(VehicleSignal.BATTERY_PERCENT, "80"),
                reading(VehicleSignal.FUEL_PERCENT, null), reading(VehicleSignal.GEAR, "P"),
            )),
        )
    }

    @Test fun `a missing value is named, not dropped or zeroed`() {
        assertEquals(
            "주행거리 미수신 · 배터리 80%",
            describeSnapshot(snapshot(reading(VehicleSignal.ODOMETER_KM, null), reading(VehicleSignal.BATTERY_PERCENT, "80"))),
        )
        assertEquals("차량 값 미수신", describeSnapshot(snapshot(reading(VehicleSignal.ODOMETER_KM, null), reading(VehicleSignal.FUEL_PERCENT, null))))
        assertEquals("차량 값 미수신", describeSnapshot(snapshot()))
    }

    @Test fun `stale, manual and simulated values are marked`() {
        assertEquals(
            "주행거리 12,480km(이전 값) · 배터리 76%(직접 입력)",
            describeSnapshot(snapshot(
                reading(VehicleSignal.ODOMETER_KM, "12480", stale = true),
                reading(VehicleSignal.BATTERY_PERCENT, "76", DataSource.USER),
            )),
        )
        assertEquals(
            "주행거리 12,480km · 배터리 76% · 시뮬레이션",
            describeSnapshot(snapshot(
                reading(VehicleSignal.ODOMETER_KM, "12480", DataSource.SIMULATED),
                reading(VehicleSignal.BATTERY_PERCENT, "76", DataSource.SIMULATED),
            )),
        )
        assertEquals("연료는 받은 차에서만", "주행거리 미수신 · 배터리 미수신 · 연료 40%",
            describeSnapshot(snapshot(reading(VehicleSignal.ODOMETER_KM, null), reading(VehicleSignal.BATTERY_PERCENT, null), reading(VehicleSignal.FUEL_PERCENT, "40"))))
    }
}
