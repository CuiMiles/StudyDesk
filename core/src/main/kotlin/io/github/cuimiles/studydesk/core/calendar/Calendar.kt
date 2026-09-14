package io.github.cuimiles.studydesk.core.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object CalendarConfig {
    const val SEMESTER_ID = "2026-fall"
    const val SEMESTER_START_DATE = "2026-09-14"
    const val TOTAL_WEEKS = 18
    const val TEACHING_WEEKS = 16
    const val EXAM_WEEKS = 2

    val HOLIDAYS = setOf(
        "2026-09-25",
        "2026-10-01",
        "2026-10-02",
        "2026-10-03",
        "2027-01-01"
    )

    val DAYS = listOf("一", "二", "三", "四", "五", "六", "日")

    val WINTER_TIMES = listOf(
        "08:00-08:50",
        "09:00-09:50",
        "10:10-11:00",
        "11:10-12:00",
        "14:00-14:50",
        "15:00-15:50",
        "16:10-17:00",
        "17:10-18:00",
        "19:10-20:00",
        "20:10-21:00",
        "21:10-22:00"
    )

    val SUMMER_TIMES = listOf(
        "08:00-08:50",
        "09:00-09:50",
        "10:10-11:00",
        "11:10-12:00",
        "14:30-15:20",
        "15:30-16:20",
        "16:40-17:30",
        "17:40-18:30",
        "19:40-20:30",
        "20:40-21:30",
        "21:40-22:30"
    )
}

val BEIJING_ZONE: ZoneOffset = ZoneOffset.ofHours(8)
val SEMESTER_START: LocalDate = LocalDate.parse(CalendarConfig.SEMESTER_START_DATE)

fun today(epochMs: Long = System.currentTimeMillis()): String {
    return Instant.ofEpochMilli(epochMs).atZone(BEIJING_ZONE).toLocalDate().toString()
}

fun addDays(date: String, n: Long): String {
    return LocalDate.parse(date).plusDays(n).toString()
}

fun shortDate(date: String): String {
    val d = LocalDate.parse(date)
    return "${d.monthValue}/${d.dayOfMonth}"
}

fun weekOf(date: String): Int {
    val target = LocalDate.parse(date)
    val days = ChronoUnit.DAYS.between(SEMESTER_START, target)
    return (Math.floorDiv(days, 7L) + 1).toInt()
}

fun dateOf(week: Int, weekday: Int): String {
    val days = (week - 1) * 7L + (weekday - 1)
    return SEMESTER_START.plusDays(days).toString()
}

fun weekdayOf(date: String): Int {
    return LocalDate.parse(date).dayOfWeek.value // 1 (Mon) to 7 (Sun)
}

fun validDate(date: String): Boolean {
    return try {
        val parsed = LocalDate.parse(date)
        parsed.toString() == date
    } catch (e: DateTimeParseException) {
        false
    }
}

fun isHoliday(date: String): Boolean {
    return CalendarConfig.HOLIDAYS.contains(date)
}

fun timesForDate(date: String): List<String> {
    val md = date.substring(5)
    return if (md in "05-01"..<"10-01") {
        CalendarConfig.SUMMER_TIMES
    } else {
        CalendarConfig.WINTER_TIMES
    }
}

fun sectionRanges(sections: Collection<Int>): List<IntRange> {
    val sorted = sections.toSet().sorted()
    if (sorted.isEmpty()) return emptyList()

    val ranges = mutableListOf<IntRange>()
    var start = sorted.first()
    var end = start

    for (i in 1 until sorted.size) {
        val n = sorted[i]
        if (n == end + 1) {
            end = n
        } else {
            ranges.add(start..end)
            start = n
            end = n
        }
    }
    ranges.add(start..end)
    return ranges
}

fun sectionLabel(sections: Collection<Int>): String {
    return sectionRanges(sections).joinToString("、") { range ->
        if (range.first == range.last) "${range.first}" else "${range.first}–${range.last}"
    }
}

fun timeLabel(date: String, sections: Collection<Int>): String {
    val t = timesForDate(date)
    return sectionRanges(sections).joinToString(" / ") { range ->
        val startStr = t[range.first - 1].substringBefore("-")
        val endStr = t[range.last - 1].substringAfter("-")
        "$startStr–$endStr"
    }
}
