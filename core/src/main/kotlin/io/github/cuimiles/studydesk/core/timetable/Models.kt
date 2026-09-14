package io.github.cuimiles.studydesk.core.timetable

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: String = "",
    val name: String,
    val className: String = "",
    val teachers: List<String> = emptyList(),
    val room: String = "",
    val weekday: Int,
    val sections: List<Int>,
    val weeks: List<Int>,
    val note: String = ""
)

@Serializable
data class CourseAdjustment(
    val courseId: String,
    val originalDate: String,
    val cancelled: Boolean = false,
    val date: String,
    val sections: List<Int>,
    val room: String = ""
)

@Serializable
data class TimetableStore(
    val schemaVersion: Int = 1,
    val semesterId: String = "2026-fall",
    val courses: List<Course> = emptyList(),
    val adjustments: List<CourseAdjustment> = emptyList(),
    val backupVersion: Int? = null
)

data class Occurrence(
    val course: Course,
    val originalDate: String,
    val date: String,
    val sections: List<Int>,
    val room: String,
    val adjusted: Boolean,
    val cancelled: Boolean
)

data class CourseTheme(
    val background: Long,
    val text: Long,
    val secondary: Long
)

data class DisplayGroup(
    val occurrence: Occurrence,
    val start: Int,
    val end: Int,
    val members: List<Occurrence> = emptyList(),
    val key: Int = 0,
    val top: Int = 0,
    val height: Int = 0,
    val conflicts: Int = 1,
    val theme: CourseTheme = CourseTheme(0xFFE2EDF8, 0xFF365B7C, 0xFF63809A)
)
