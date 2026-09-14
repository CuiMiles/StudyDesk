package io.github.cuimiles.studydesk.data

import android.content.Context
import io.github.cuimiles.studydesk.core.calendar.today
import io.github.cuimiles.studydesk.core.timetable.Course
import io.github.cuimiles.studydesk.core.timetable.CourseAdjustment
import io.github.cuimiles.studydesk.core.timetable.DisplayGroup
import io.github.cuimiles.studydesk.core.timetable.TimetableBackup
import io.github.cuimiles.studydesk.core.timetable.TimetableEngine
import io.github.cuimiles.studydesk.core.timetable.TimetableStore
import io.github.cuimiles.studydesk.core.vocabulary.*

class StudyDeskRepository(context: Context) {

    val contentDb = ContentDatabaseHelper(context)
    val userDb = UserDatabaseHelper(context)

    init {
        // First-run initialization of default badminton course
        val initialized = userDb.getSetting("badminton_initialized")
        if (initialized != "true") {
            val existing = userDb.getAllCourses()
            if (!existing.any { TimetableEngine.isBadmintonCourse(it) }) {
                userDb.saveCourse(TimetableEngine.defaultBadmintonCourse())
            }
            userDb.setSetting("badminton_initialized", "true")
        }
    }

    // Timetable
    fun getCourses(): List<Course> = userDb.getAllCourses()
    fun saveCourse(course: Course) = userDb.saveCourse(course)
    fun deleteCourse(courseId: String) = userDb.deleteCourse(courseId)
    fun getAdjustments(): List<CourseAdjustment> = userDb.getAllAdjustments()
    fun saveAdjustment(adj: CourseAdjustment) = userDb.saveAdjustment(adj)
    fun deleteAdjustment(courseId: String, originalDate: String) = userDb.deleteAdjustment(courseId, originalDate)

    fun getWeekSchedule(week: Int): List<DisplayGroup> {
        val courses = getCourses()
        val adjustments = getAdjustments()
        val occurrences = TimetableEngine.schedule(courses, adjustments, week)
        return TimetableEngine.blocks(occurrences)
    }

    fun importTimetable(text: String, supplementBadminton: Boolean): Int {
        val parsed = TimetableBackup.parse(text, isBackup = false)
        val courses = if (supplementBadminton) {
            TimetableEngine.supplementBadminton(parsed.courses)
        } else {
            parsed.courses
        }
        userDb.clearAndSetCourses(courses)
        return courses.size
    }

    fun restoreFullBackup(text: String) {
        val parsed = TimetableBackup.parse(text, isBackup = true)
        userDb.clearAndSetCourses(parsed.courses)
        userDb.clearAndSetAdjustments(parsed.adjustments)
    }

    fun exportFullBackup(): String {
        val store = TimetableStore(
            schemaVersion = 1,
            semesterId = "2026-fall",
            courses = getCourses(),
            adjustments = getAdjustments(),
            backupVersion = 1
        )
        return TimetableBackup.exportBackup(store)
    }

    // Vocabulary
    private var cachedStore: VocabularyStore? = null

    fun getVocabularyStore(): VocabularyStore {
        if (cachedStore == null) {
            val progressMap = userDb.getProgressMap()
            val favorites = userDb.getFavorites().toList()
            val limitStr = userDb.getSetting("daily_new_limit", "20")
            val limit = limitStr.toIntOrNull() ?: 20
            cachedStore = VocabularyStore(
                settings = UserVocabularySettings(dailyNewLimit = limit),
                progress = progressMap,
                favorites = favorites
            )
        }
        return cachedStore!!
    }

    fun loadTodaySession(): VocabularyStore {
        val currentDay = today()
        val baseStore = getVocabularyStore()
        val allWordIds = contentDb.getAllWordIds()
        val updated = VocabularyEngine.buildQueue(baseStore, allWordIds, currentDay)
        cachedStore = updated
        return updated
    }

    fun rateWord(attemptId: String, rating: Rating): VocabularyStore {
        val currentDay = today()
        val store = getVocabularyStore()
        val updated = VocabularyEngine.rate(store, attemptId, rating, currentDay)
        cachedStore = updated
        userDb.saveProgressMap(updated.progress)
        return updated
    }

    fun markMastered(wordId: String): VocabularyStore {
        val store = getVocabularyStore()
        val updated = VocabularyEngine.markFamiliar(store, wordId)
        cachedStore = updated
        val p = updated.progress[wordId]
        if (p != null) userDb.saveProgress(wordId, p)
        return updated
    }

    fun relearn(wordId: String): VocabularyStore {
        val currentDay = today()
        val store = getVocabularyStore()
        val updated = VocabularyEngine.relearn(store, wordId, currentDay)
        cachedStore = updated
        val p = updated.progress[wordId]
        if (p != null) userDb.saveProgress(wordId, p)
        return updated
    }

    fun toggleFavorite(wordId: String): VocabularyStore {
        val store = getVocabularyStore()
        val isFav = store.favorites.contains(wordId)
        userDb.setFavorite(wordId, !isFav)
        val updated = VocabularyEngine.toggleFavorite(store, wordId)
        cachedStore = updated
        return updated
    }

    fun setDailyNewLimit(limit: Int) {
        userDb.setSetting("daily_new_limit", limit.toString())
        val store = getVocabularyStore()
        cachedStore = store.copy(settings = store.settings.copy(dailyNewLimit = limit))
    }

    fun clearAllUserData() {
        userDb.clearAllUserData()
        cachedStore = null
    }
}
