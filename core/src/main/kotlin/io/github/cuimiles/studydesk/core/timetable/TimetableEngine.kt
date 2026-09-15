package io.github.cuimiles.studydesk.core.timetable

import io.github.cuimiles.studydesk.core.calendar.isHoliday
import io.github.cuimiles.studydesk.core.calendar.dateOf
import io.github.cuimiles.studydesk.core.calendar.weekOf
import kotlin.math.abs

object TimetableEngine {

    val PALETTE = listOf(
        CourseTheme(0xFFE2EDF8, 0xFF365B7C, 0xFF63809A),
        CourseTheme(0xFFE5EFDF, 0xFF456640, 0xFF73896B),
        CourseTheme(0xFFF6EFCF, 0xFF78652F, 0xFF9A8853),
        CourseTheme(0xFFEEE6F6, 0xFF66517F, 0xFF8B789F),
        CourseTheme(0xFFF5E4EB, 0xFF86576C, 0xFFA67F90),
        CourseTheme(0xFFDEEFED, 0xFF396D69, 0xFF6C928E),
        CourseTheme(0xFFF7E8D8, 0xFF825E3D, 0xFFA1876B)
    )

    fun defaultBadmintonCourse(): Course {
        return Course(
            id = "default-badminton-2026",
            name = "羽毛球",
            className = "",
            teachers = listOf("胡浩"),
            room = "2号巨构七楼羽毛球场",
            weekday = 3,
            sections = listOf(3, 4),
            weeks = (1..8).toList(),
            note = ""
        )
    }

    fun isBadmintonCourse(course: Course): Boolean {
        val normName = normalize(course.name)
        val normSecs = course.sections.toSet().sorted()
        return normName == "羽毛球" && course.weekday == 3 && normSecs == listOf(3, 4)
    }

    fun supplementBadminton(courses: List<Course>): List<Course> {
        val exists = courses.any { isBadmintonCourse(it) }
        return if (exists) {
            courses
        } else {
            courses + defaultBadmintonCourse()
        }
    }

    fun normalize(s: String): String {
        return s.trim().replace(Regex("\\s+"), "").replace("（", "(").replace("）", ")")
    }

    fun courseTheme(name: String): CourseTheme {
        var h = 0
        for (ch in normalize(name)) {
            h = (h * 31 + ch.code)
        }
        val idx = Math.floorMod(h, PALETTE.size)
        return PALETTE[idx]
    }

    fun occurrence(
        courses: List<Course>,
        adjustments: List<CourseAdjustment>,
        courseId: String,
        originalDate: String
    ): Occurrence? {
        val course = courses.find { it.id == courseId } ?: return null
        val adj = adjustments.find { it.courseId == courseId && it.originalDate == originalDate }
        return Occurrence(
            course = course,
            originalDate = originalDate,
            date = adj?.date ?: originalDate,
            sections = adj?.sections ?: course.sections,
            room = adj?.room ?: course.room,
            adjusted = adj != null,
            cancelled = adj?.cancelled ?: false
        )
    }

    fun schedule(
        courses: List<Course>,
        adjustments: List<CourseAdjustment>,
        week: numberWeek
    ): List<Occurrence> {
        val out = mutableListOf<Occurrence>()
        for (course in courses) {
            for (w in course.weeks) {
                val original = dateOf(w, course.weekday)
                val occ = occurrence(courses, adjustments, course.id, original) ?: continue
                if (!occ.cancelled && weekOf(occ.date) == week && (occ.adjusted || !isHoliday(original))) {
                    out.add(occ)
                }
            }
        }
        out.sortWith(compareBy<Occurrence> { it.date }.thenBy { it.sections.minOrNull() ?: 0 })
        return out
    }

    private data class InternalGroup(
        var occurrence: Occurrence,
        var start: Int,
        var end: Int,
        val members: MutableList<Occurrence>
    )

    private fun isSameLesson(a: InternalGroup, b: InternalGroup): Boolean {
        return a.occurrence.date == b.occurrence.date &&
                a.occurrence.originalDate == b.occurrence.originalDate &&
                normalize(a.occurrence.course.name) == normalize(b.occurrence.course.name) &&
                normalize(a.occurrence.room) == normalize(b.occurrence.room) &&
                normalize(a.occurrence.course.className) == normalize(b.occurrence.course.className) &&
                a.occurrence.course.note.trim() == b.occurrence.course.note.trim() &&
                a.occurrence.adjusted == b.occurrence.adjusted &&
                a.occurrence.course.teachers.map { normalize(it) }.sorted() ==
                b.occurrence.course.teachers.map { normalize(it) }.sorted()
    }

    fun blocks(items: List<Occurrence>, rowHeightPx: Int = 116): List<DisplayGroup> {
        val rawGroups = mutableListOf<InternalGroup>()

        for (occ in items) {
            val sections = occ.sections.toSet().sorted()
            if (sections.isEmpty()) continue

            var start = sections[0]
            var end = start

            fun emit() {
                val secList = (start..end).toList()
                val copyOcc = occ.copy(sections = secList)
                rawGroups.add(
                    InternalGroup(
                        occurrence = copyOcc,
                        start = start,
                        end = end,
                        members = mutableListOf(occ)
                    )
                )
            }

            for (i in 1 until sections.size) {
                val n = sections[i]
                if (n == end + 1) {
                    end = n
                } else {
                    emit()
                    start = n
                    end = n
                }
            }
            emit()
        }

        rawGroups.sortWith(compareBy<InternalGroup> { it.occurrence.date }.thenBy { it.start })

        val merged = mutableListOf<InternalGroup>()
        for (g in rawGroups) {
            val prev = merged.find { it.end + 1 == g.start && isSameLesson(it, g) }
            if (prev != null) {
                prev.end = g.end
                val combinedSections = prev.occurrence.sections + g.occurrence.sections
                prev.occurrence = prev.occurrence.copy(sections = combinedSections)
                for (m in g.members) {
                    if (prev.members.none { it.course.id == m.course.id && it.originalDate == m.originalDate }) {
                        prev.members.add(m)
                    }
                }
            } else {
                merged.add(InternalGroup(g.occurrence, g.start, g.end, g.members.toMutableList()))
            }
        }

        return merged.mapIndexed { index, b ->
            val conflictCount = merged.count { x ->
                x.occurrence.date == b.occurrence.date && x.start <= b.end && x.end >= b.start
            }
            DisplayGroup(
                occurrence = b.occurrence,
                start = b.start,
                end = b.end,
                members = b.members,
                key = index,
                top = (b.start - 1) * rowHeightPx,
                height = (b.end - b.start + 1) * rowHeightPx - 6,
                conflicts = conflictCount,
                theme = courseTheme(b.occurrence.course.name)
            )
        }
    }
}

typealias numberWeek = Int
