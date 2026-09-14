package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.timetable.Course
import io.github.cuimiles.studydesk.core.timetable.CourseAdjustment
import io.github.cuimiles.studydesk.core.timetable.TimetableBackup
import io.github.cuimiles.studydesk.core.timetable.TimetableStore
import org.junit.Assert.*
import org.junit.Test

class TimetableBackupTest {

    private val sampleJson = """
        {
          "schemaVersion": 1,
          "semesterId": "2026-fall",
          "courses": [
            {
              "name": "数据库系统原理与应用",
              "className": "1班",
              "teachers": ["刘帅"],
              "room": "5-2W201",
              "weekday": 6,
              "sections": [1, 2],
              "weeks": [1, 2, 3, 4, 5, 6, 7, 8],
              "note": ""
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testParseStandardImport() {
        val store = TimetableBackup.parse(sampleJson, isBackup = false)
        assertEquals(1, store.schemaVersion)
        assertEquals("2026-fall", store.semesterId)
        assertEquals(1, store.courses.size)
        val course = store.courses[0]
        assertEquals("数据库系统原理与应用", course.name)
        assertEquals(6, course.weekday)
        assertEquals(listOf(1, 2), course.sections)
        assertTrue(course.id.isNotBlank())
    }

    @Test
    fun testExportAndRestoreFullBackup() {
        val course = Course(
            id = "course-123",
            name = "计算机网络",
            weekday = 2,
            sections = listOf(1, 2),
            weeks = listOf(1, 2)
        )
        val adj = CourseAdjustment(
            courseId = "course-123",
            originalDate = "2026-09-15",
            cancelled = false,
            date = "2026-09-16",
            sections = listOf(3, 4),
            room = "主A-201"
        )
        val store = TimetableStore(
            schemaVersion = 1,
            semesterId = "2026-fall",
            courses = listOf(course),
            adjustments = listOf(adj),
            backupVersion = 1
        )

        val exported = TimetableBackup.exportBackup(store)
        val restored = TimetableBackup.parse(exported, isBackup = true)

        assertEquals(1, restored.courses.size)
        assertEquals("course-123", restored.courses[0].id)
        assertEquals(1, restored.adjustments.size)
        assertEquals("2026-09-15", restored.adjustments[0].originalDate)
        assertEquals("2026-09-16", restored.adjustments[0].date)
        assertEquals(listOf(3, 4), restored.adjustments[0].sections)
    }

    @Test
    fun testRejectInvalidData() {
        // Missing required schemaVersion
        assertThrows(IllegalArgumentException::class.java) {
            TimetableBackup.parse("""{"semesterId":"2026-fall","courses":[]}""")
        }

        // Invalid weekday
        assertThrows(IllegalArgumentException::class.java) {
            TimetableBackup.parse("""{"schemaVersion":1,"semesterId":"2026-fall","courses":[{"name":"A","weekday":8,"sections":[1],"weeks":[1]}]}""")
        }
    }
}
