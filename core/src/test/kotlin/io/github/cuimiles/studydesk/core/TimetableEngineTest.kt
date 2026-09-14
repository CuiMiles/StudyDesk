package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.timetable.*
import org.junit.Assert.*
import org.junit.Test

class TimetableEngineTest {

    @Test
    fun testDefaultBadmintonCourse() {
        val badminton = TimetableEngine.defaultBadmintonCourse()
        assertEquals("羽毛球", badminton.name)
        assertEquals(3, badminton.weekday) // Wednesday
        assertEquals(listOf(3, 4), badminton.sections)
        assertEquals((1..8).toList(), badminton.weeks)
        assertEquals("2号巨构七楼羽毛球场", badminton.room)
        assertEquals(listOf("胡浩"), badminton.teachers)
        assertTrue(TimetableEngine.isBadmintonCourse(badminton))
    }

    @Test
    fun testSupplementBadminton() {
        val emptyCourses = emptyList<Course>()
        val supplemented = TimetableEngine.supplementBadminton(emptyCourses)
        assertEquals(1, supplemented.size)
        assertTrue(TimetableEngine.isBadmintonCourse(supplemented[0]))

        // Calling again doesn't duplicate
        val doubleCheck = TimetableEngine.supplementBadminton(supplemented)
        assertEquals(1, doubleCheck.size)
    }

    @Test
    fun testScheduleBadmintonWeeks() {
        val badminton = TimetableEngine.defaultBadmintonCourse()
        val courses = listOf(badminton)

        // Week 1 should have badminton on Wednesday 2026-09-16
        val week1 = TimetableEngine.schedule(courses, emptyList(), 1)
        assertEquals(1, week1.size)
        assertEquals("2026-09-16", week1[0].date)
        assertEquals(listOf(3, 4), week1[0].sections)

        // Week 8 should have badminton on Wednesday 2026-11-04
        val week8 = TimetableEngine.schedule(courses, emptyList(), 8)
        assertEquals(1, week8.size)
        assertEquals("2026-11-04", week8[0].date)

        // Week 9 should not have badminton
        val week9 = TimetableEngine.schedule(courses, emptyList(), 9)
        assertTrue(week9.isEmpty())
    }

    @Test
    fun testHolidayFilteringAndAdjustment() {
        // Create a Friday class on week 2: date 2026-09-25 is holiday!
        val fridayCourse = Course(
            id = "c1",
            name = "线性代数",
            room = "中2-1201",
            weekday = 5,
            sections = listOf(1, 2),
            weeks = listOf(2)
        )
        val courses = listOf(fridayCourse)

        // Week 2: 2026-09-25 is holiday, should be filtered out
        val week2 = TimetableEngine.schedule(courses, emptyList(), 2)
        assertTrue("Course on holiday should be hidden", week2.isEmpty())

        // If explicitly adjusted to another day, it should show
        val adj = CourseAdjustment(
            courseId = "c1",
            originalDate = "2026-09-25",
            date = "2026-09-24", // Thursday
            sections = listOf(1, 2),
            room = "中2-1201"
        )
        val week2Adjusted = TimetableEngine.schedule(courses, listOf(adj), 2)
        assertEquals(1, week2Adjusted.size)
        assertEquals("2026-09-24", week2Adjusted[0].date)
        assertTrue(week2Adjusted[0].adjusted)
    }

    @Test
    fun testCrossWeekAdjustmentAndCancellation() {
        val course = Course(
            id = "c1",
            name = "大学物理",
            weekday = 1,
            sections = listOf(1, 2),
            weeks = listOf(1, 2)
        )
        val courses = listOf(course)

        // Move week 1 Monday (2026-09-14) to week 2 Tuesday (2026-09-22)
        val moveAdj = CourseAdjustment(
            courseId = "c1",
            originalDate = "2026-09-14",
            date = "2026-09-22",
            sections = listOf(3, 4),
            room = "教2"
        )
        // Cancel week 2 Monday (2026-09-21)
        val cancelAdj = CourseAdjustment(
            courseId = "c1",
            originalDate = "2026-09-21",
            cancelled = true,
            date = "2026-09-21",
            sections = listOf(1, 2)
        )

        val adjustments = listOf(moveAdj, cancelAdj)

        // Week 1 should now be empty (moved away)
        val week1 = TimetableEngine.schedule(courses, adjustments, 1)
        assertTrue(week1.isEmpty())

        // Week 2 should contain only the moved class on Tuesday 2026-09-22
        val week2 = TimetableEngine.schedule(courses, adjustments, 2)
        assertEquals(1, week2.size)
        assertEquals("2026-09-22", week2[0].date)
        assertEquals(listOf(3, 4), week2[0].sections)
    }

    @Test
    fun testConsecutiveLessonMerging() {
        val coursePart1 = Course(
            id = "c1",
            name = "软件工程",
            className = "计科1班",
            teachers = listOf("张老师"),
            room = "中1-101",
            weekday = 1,
            sections = listOf(1, 2),
            weeks = listOf(1)
        )
        val coursePart2 = Course(
            id = "c2",
            name = "软件工程",
            className = "计科1班",
            teachers = listOf("张老师"),
            room = "中1-101",
            weekday = 1,
            sections = listOf(3),
            weeks = listOf(1)
        )
        val courses = listOf(coursePart1, coursePart2)
        val occurrences = TimetableEngine.schedule(courses, emptyList(), 1)
        assertEquals(2, occurrences.size)

        val displayBlocks = TimetableEngine.blocks(occurrences)
        assertEquals("Consecutive lessons should be merged into 1 card", 1, displayBlocks.size)
        assertEquals(1, displayBlocks[0].start)
        assertEquals(3, displayBlocks[0].end)
        assertEquals(listOf(1, 2, 3), displayBlocks[0].occurrence.sections)
    }
}
