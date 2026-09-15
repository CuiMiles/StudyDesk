package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.vocabulary.*
import io.github.cuimiles.studydesk.core.backup.WorkspaceBackup
import io.github.cuimiles.studydesk.core.timetable.TimetableStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AdaptiveLearningTest {
    private val settings = UserVocabularySettings()
    @Test fun scoreBoundariesAndLongInterval() {
        var p = AdaptiveScheduler.next(UserProgress(), Rating.UNKNOWN, "2026-09-15", settings, false)
        assertEquals(0, p.recognition)
        for (day in listOf("2026-09-16", "2026-09-17", "2026-09-20")) p = AdaptiveScheduler.next(p, Rating.KNOWN, day, settings, false)
        assertEquals(3, p.recognition)
        assertTrue(p.intervalDays >= 30)
        val store = VocabularyStore(progress = mapOf("a" to p))
        assertTrue(VocabularyEngine.buildQueue(store, listOf("a"), "2026-09-21").session!!.queue.isEmpty())
        assertEquals(3, AdaptiveScheduler.next(p, Rating.KNOWN, "2026-12-20", settings, false).recognition)
    }
    @Test fun delayedForgettingCreatesDifficultWordAndRecoveryRemovesIt() {
        var p = UserProgress(recognition = 1, last = "2026-09-14")
        p = AdaptiveScheduler.next(p, Rating.UNKNOWN, "2026-09-15", settings, false)
        assertFalse(p.difficult)
        p = AdaptiveScheduler.next(p, Rating.KNOWN, "2026-09-16", settings, false)
        p = AdaptiveScheduler.next(p, Rating.UNKNOWN, "2026-09-18", settings, false)
        assertEquals(2, p.delayedLapses)
        assertTrue(p.difficult)
        assertEquals(1, p.intervalDays)
        for (day in listOf("2026-09-19", "2026-09-20", "2026-09-21")) p = AdaptiveScheduler.next(p, Rating.KNOWN, day, settings, false)
        assertFalse(p.difficult)
    }
    @Test fun immediateRetriesAreNotDelayedLapses() {
        val p = AdaptiveScheduler.next(UserProgress(recognition=1,last="2026-09-15"), Rating.UNKNOWN,"2026-09-15",settings,false)
        assertEquals(0,p.delayedLapses)
    }
    @Test fun personalSettingsAndDifficultyChangeInterval() {
        val base = UserProgress(recognition=1,difficulty=5.0,last="2026-09-14",difficult=true)
        val manual = settings.copy(shortIntervals=listOf(2,10),longIntervalDays=120,adaptiveReview=false)
        assertEquals(10,AdaptiveScheduler.next(base,Rating.KNOWN,"2026-09-15",manual,false).intervalDays)
        assertTrue(AdaptiveScheduler.next(base,Rating.KNOWN,"2026-09-15",manual.copy(adaptiveReview=true),false).intervalDays < 10)
    }
    @Test fun highlightingMatchesWordsAndKnownFormsNotSubstrings() {
        val text = "Run, RUNNING and runner; he ran."
        assertEquals(listOf("Run","RUNNING","ran"), ExampleText.matches(text,listOf("run","running","ran")).map { text.substring(it) })
        assertEquals(1,ExampleText.matches("an easy-going child",listOf("easy-going")).size)
    }
    @Test fun oldProgressDoesNotInventRecognition() {
        val p = Json.decodeFromString<UserProgress>("{\"stage\":5,\"state\":\"REVIEW\"}")
        assertEquals(0,p.recognition)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidRecognitionBackupRejected() {
        WorkspaceBackup.parse(Json.encodeToString(WorkspaceBackup(timetable=TimetableStore(backupVersion=1),
            vocabulary=VocabularyStore(progress=mapOf("a" to UserProgress(recognition=4))))))
    }
}
