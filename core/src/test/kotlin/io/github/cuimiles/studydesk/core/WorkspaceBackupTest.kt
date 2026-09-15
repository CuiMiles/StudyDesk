package io.github.cuimiles.studydesk.core

import io.github.cuimiles.studydesk.core.backup.WorkspaceBackup
import io.github.cuimiles.studydesk.core.timetable.*
import io.github.cuimiles.studydesk.core.vocabulary.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class WorkspaceBackupTest {
    @Test fun restartPreservesRetryOrderAndDailyLimit() {
        val day = "2026-09-15"
        val ids = listOf("a", "b", "c", "d", "e", "f")
        var store = VocabularyEngine.buildQueue(VocabularyStore(settings = UserVocabularySettings(3)), ids, day)
        store = VocabularyEngine.rate(store, store.session!!.queue.first().id, Rating.UNKNOWN, day)
        val backup = WorkspaceBackup(timetable = TimetableStore(backupVersion = 1), vocabulary = store)
        val restored = WorkspaceBackup.parse(Json.encodeToString(backup)).vocabulary
        assertEquals(store, restored)
        val rebuilt = VocabularyEngine.buildQueue(restored, ids, day)
        assertEquals(store.session!!.queue, rebuilt.session!!.queue)
        assertEquals(2, rebuilt.session!!.queue.count { it.mode == AttemptMode.NEW })
        assertEquals(1, rebuilt.daily[day]!!.newIds.size)
        assertEquals(1, rebuilt.session!!.retries["a"])
    }
    @Test fun backupPreservesFamiliarFavoritesAndSpelling() {
        val v = VocabularyStore(progress = mapOf("a" to UserProgress(ProgressState.FAMILIAR)),
            favorites = listOf("a"), daily = mapOf("2026-09-15" to DailyStats(spellingAttempts = 2, spellingCorrect = 1)))
        val backup = WorkspaceBackup(timetable = TimetableStore(backupVersion = 1), vocabulary = v)
        assertEquals(backup, WorkspaceBackup.parse(Json.encodeToString(backup)))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidLimitRejected() {
        WorkspaceBackup.parse(Json.encodeToString(WorkspaceBackup(timetable = TimetableStore(backupVersion = 1),
            vocabulary = VocabularyStore(settings = UserVocabularySettings(101)))))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidDateRejected() {
        WorkspaceBackup.parse(Json.encodeToString(WorkspaceBackup(timetable = TimetableStore(backupVersion = 1),
            vocabulary = VocabularyStore(progress = mapOf("a" to UserProgress(due = "bad"))))))
    }
}
