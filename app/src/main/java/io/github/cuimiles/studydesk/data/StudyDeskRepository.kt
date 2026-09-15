package io.github.cuimiles.studydesk.data

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import io.github.cuimiles.studydesk.core.backup.WorkspaceBackup
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
    fun saveCourse(course: Course) {
        val courses = getCourses().filterNot { it.id == course.id } + course
        val store = TimetableStore(courses = courses, adjustments = getAdjustments(), backupVersion = 1)
        TimetableBackup.parse(TimetableBackup.exportBackup(store), true)
        userDb.saveCourse(course)
    }
    fun deleteCourse(courseId: String) = userDb.deleteCourse(courseId)
    fun getAdjustments(): List<CourseAdjustment> = userDb.getAllAdjustments()
    fun saveAdjustment(adj: CourseAdjustment) {
        val adjustments = getAdjustments().filterNot { it.courseId == adj.courseId && it.originalDate == adj.originalDate } + adj
        TimetableBackup.parse(TimetableBackup.exportBackup(TimetableStore(courses = getCourses(), adjustments = adjustments, backupVersion = 1)), true)
        userDb.saveAdjustment(adj)
    }
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
        userDb.transaction {
            userDb.clearAndSetCourses(courses)
            userDb.clearAndSetAdjustments(emptyList())
        }
        return courses.size
    }

    fun restoreFullBackup(text: String) {
        val root = Json.parseToJsonElement(text).jsonObject
        if (root.containsKey("workspaceVersion")) {
            val backup = WorkspaceBackup.parse(text)
            userDb.transaction {
                userDb.clearAndSetCourses(backup.timetable.courses)
                userDb.clearAndSetAdjustments(backup.timetable.adjustments)
                userDb.setSetting("vocabulary_store", Json.encodeToString(backup.vocabulary))
                userDb.setSetting("badminton_initialized", "true")
            }
            cachedStore = backup.vocabulary
        } else {
            val parsed = TimetableBackup.parse(text, isBackup = true)
            userDb.transaction {
                userDb.clearAndSetCourses(parsed.courses)
                userDb.clearAndSetAdjustments(parsed.adjustments)
                userDb.setSetting("badminton_initialized", "true")
            }
        }
    }

    fun exportFullBackup(): String = Json { encodeDefaults = true }.encodeToString(
        WorkspaceBackup(timetable = TimetableStore(courses = getCourses(),
            adjustments = getAdjustments(), backupVersion = 1), vocabulary = getVocabularyStore())
    )

    private fun persist(store: VocabularyStore): VocabularyStore {
        userDb.setSetting("vocabulary_store", Json.encodeToString(store))
        cachedStore = store
        return store
    }

    fun recordSpelling(wordId: String, correct: Boolean) {
        val store = getVocabularyStore()
        val day = today()
        val stats = store.daily[day] ?: DailyStats()
        persist(store.copy(revision = store.revision + 1, daily = store.daily + (day to stats.copy(
            spellingAttempts = stats.spellingAttempts + 1,
            spellingCorrect = stats.spellingCorrect + if (correct) 1 else 0,
            spellingIds = (stats.spellingIds + wordId).distinct()))))
    }

    // Vocabulary
    private var cachedStore: VocabularyStore? = null

    fun getVocabularyStore(): VocabularyStore {
        if (cachedStore == null) {
            val saved = userDb.getSetting("vocabulary_store")
            if (saved.isNotBlank()) {
                cachedStore = Json.decodeFromString<VocabularyStore>(saved)
                return cachedStore!!
            }
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
        return persist(updated)
    }

    fun rateWord(attemptId: String, rating: Rating): VocabularyStore {
        val currentDay = today()
        val store = getVocabularyStore()
        if (store.pendingAnswer != null || store.events.any { it.id == attemptId }) return store
        if (store.session?.day != currentDay) return loadTodaySession()
        if (store.session?.queue?.firstOrNull()?.id != attemptId) return store
        val updated = VocabularyEngine.rate(store, attemptId, rating, currentDay)
        return persist(updated.copy(pendingAnswer = AnswerCard(store.session!!.queue.first(), rating, currentDay)))
    }

    fun nextWord(): VocabularyStore {
        persist(getVocabularyStore().copy(pendingAnswer = null))
        return loadTodaySession()
    }

    fun markAutoSpoken(attemptId: String) { persist(getVocabularyStore().copy(autoSpokenAttemptId = attemptId)) }

    fun setReviewSettings(first: Int, second: Int, long: Int, adaptive: Boolean) {
        require(first in 1..7 && second in first..30 && long in 30..365)
        val store = getVocabularyStore()
        persist(store.copy(settings = store.settings.copy(shortIntervals = listOf(first, second), longIntervalDays = long, adaptiveReview = adaptive)))
    }

    fun markMastered(wordId: String): VocabularyStore {
        val store = getVocabularyStore()
        val updated = VocabularyEngine.markFamiliar(store, wordId)
        return persist(updated)
    }

    fun relearn(wordId: String): VocabularyStore {
        val currentDay = today()
        val store = getVocabularyStore()
        val updated = VocabularyEngine.relearn(store, wordId, currentDay)
        return persist(updated)
    }

    fun toggleFavorite(wordId: String): VocabularyStore {
        val store = getVocabularyStore()
        val updated = VocabularyEngine.toggleFavorite(store, wordId)
        return persist(updated)
    }

    fun setDailyNewLimit(limit: Int) {
        require(limit in 0..100)
        val store = getVocabularyStore()
        persist(store.copy(settings = store.settings.copy(dailyNewLimit = limit)))
    }

    fun clearAllUserData() {
        userDb.transaction {
            userDb.clearAllUserData()
            userDb.setSetting("badminton_initialized", "true")
        }
        cachedStore = null
    }
}
