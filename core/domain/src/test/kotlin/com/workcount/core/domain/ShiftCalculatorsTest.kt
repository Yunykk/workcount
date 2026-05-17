package com.workcount.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class ShiftCalculatorsTest {
    private val settings = PaySettings(defaultHourlyRate = 15.0)

    @Test
    fun `payable duration subtracts stored and live break`() {
        val shift = Shift(
            id = "1",
            startedAtEpochMillis = 0L,
            endedAtEpochMillis = null,
            status = ShiftStatus.PAUSED,
            breakSeconds = 600L,
            pausedAtEpochMillis = 7_200_000L,
        )

        val payable = ShiftCalculators.payableDurationSeconds(shift, nowEpochMillis = 10_800_000L)

        assertEquals(6_600L, payable)
    }

    @Test
    fun `earnings respect rounding mode exact`() {
        val shift = Shift(
            id = "2",
            startedAtEpochMillis = 0L,
            endedAtEpochMillis = 4_200_000L,
            hourlyRateSnapshot = 15.0,
        )

        val earnings = ShiftCalculators.earnings(shift, settings, nowEpochMillis = 4_200_000L)

        assertEquals(17.5, earnings)
    }

    @Test
    fun `earnings respect quarter hour rounding`() {
        val shift = Shift(
            id = "3",
            startedAtEpochMillis = 0L,
            endedAtEpochMillis = 4_200_000L,
            hourlyRateSnapshot = 15.0,
        )

        val earnings = ShiftCalculators.earnings(
            shift = shift,
            settings = settings.copy(roundingMode = RoundingMode.QUARTER_HOUR),
            nowEpochMillis = 4_200_000L,
        )

        assertEquals(18.75, earnings)
    }

    @Test
    fun `shift snapshot rate wins over place and default`() {
        val shift = Shift(
            id = "4",
            startedAtEpochMillis = 0L,
            endedAtEpochMillis = 3_600_000L,
            hourlyRateSnapshot = 30.0,
        )
        val place = PlacePreset(id = "p", name = "Warehouse", defaultHourlyRate = 20.0)

        val earnings = ShiftCalculators.earnings(
            shift = shift,
            settings = settings.copy(defaultHourlyRate = 15.0),
            nowEpochMillis = 3_600_000L,
            placePreset = place,
        )

        assertEquals(30.0, earnings)
    }
}
