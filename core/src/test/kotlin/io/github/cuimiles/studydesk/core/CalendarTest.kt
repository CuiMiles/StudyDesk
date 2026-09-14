package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.calendar.*
import org.junit.Assert.*
import org.junit.Test

class CalendarTest {

    @Test
    fun testSemesterStartAndWeeks() {
        assertEquals("2026-09-14", CalendarConfig.SEMESTER_START_DATE)
        assertEquals(1, weekOf("2026-09-14"))
        assertEquals(1, weekdayOf("2026-09-14")) // Monday

        assertEquals("2026-09-14", dateOf(1, 1))
        assertEquals("2026-09-20", dateOf(1, 7)) // Sunday
        assertEquals(1, weekOf("2026-09-20"))

        assertEquals("2026-09-21", dateOf(2, 1))
        assertEquals(2, weekOf("2026-09-21"))

        assertEquals("2027-01-17", dateOf(18, 7))
        assertEquals(18, weekOf("2027-01-17"))
    }

    @Test
    fun testHolidays() {
        assertTrue(isHoliday("2026-09-25"))
        assertTrue(isHoliday("2026-10-01"))
        assertTrue(isHoliday("2026-10-02"))
        assertTrue(isHoliday("2026-10-03"))
        assertTrue(isHoliday("2027-01-01"))

        assertFalse(isHoliday("2026-09-24"))
        assertFalse(isHoliday("2026-10-04"))
    }

    @Test
    fun testSummerAndWinterTimes() {
        // September is Summer
        val summer = timesForDate("2026-09-20")
        assertEquals("14:30-15:20", summer[4]) // Period 5

        // October is Winter
        val winter = timesForDate("2026-10-15")
        assertEquals("14:00-14:50", winter[4]) // Period 5
    }

    @Test
    fun testSectionRangesAndLabels() {
        val ranges = sectionRanges(listOf(1, 2, 3, 5, 6))
        assertEquals(2, ranges.size)
        assertEquals(1..3, ranges[0])
        assertEquals(5..6, ranges[1])

        assertEquals("1–3、5–6", sectionLabel(listOf(1, 2, 3, 5, 6)))
        assertEquals("1", sectionLabel(listOf(1)))

        val timeLbl = timeLabel("2026-09-20", listOf(1, 2))
        assertEquals("08:00–09:50", timeLbl)
    }

    @Test
    fun testValidDate() {
        assertTrue(validDate("2026-09-14"))
        assertFalse(validDate("2026-02-30"))
        assertFalse(validDate("invalid"))
    }
}
