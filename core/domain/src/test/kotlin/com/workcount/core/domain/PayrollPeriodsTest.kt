package com.workcount.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDate
import java.time.ZoneId

class PayrollPeriodsTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `week range starts on monday`() {
        val range = PayrollPeriods.week(LocalDate.parse("2026-04-08"), zone)

        assertEquals("2026-04-06 - 2026-04-12", range.label)
    }

    @Test
    fun `month range covers full month`() {
        val range = PayrollPeriods.month(LocalDate.parse("2026-02-10"), zone)

        assertEquals("2026-02", range.label)
    }
}
