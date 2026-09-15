package io.github.cuimiles.studydesk

import io.github.cuimiles.studydesk.data.StudyDeskRepository
import io.github.cuimiles.studydesk.core.vocabulary.Rating
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryTest {
    @Test fun actualDatabaseSurvivesRecreationAndFullRestore() {
        val context = RuntimeEnvironment.getApplication()
        var repo = StudyDeskRepository(context)
        assertEquals(3611, repo.contentDb.getBook()!!.entryCount)
        repo.setDailyNewLimit(3)
        val session = repo.loadTodaySession()
        val attempt = session.session!!.queue.first()
        val rated = repo.rateWord(attempt.id, Rating.UNKNOWN)
        assertEquals(rated, repo.rateWord(attempt.id, Rating.UNKNOWN))
        repo.toggleFavorite(attempt.entryId)
        repo.recordSpelling(attempt.entryId, true)
        val before = repo.getVocabularyStore()
        val exported = repo.exportFullBackup()
        repo.userDb.close(); repo.contentDb.close()
        repo = StudyDeskRepository(context)
        assertEquals(before, repo.getVocabularyStore())
        assertEquals(before.session, repo.loadTodaySession().session)
        repo.clearAllUserData()
        assertTrue(repo.getVocabularyStore().progress.isEmpty())
        repo.restoreFullBackup(exported)
        assertEquals(before, repo.getVocabularyStore())
        assertEquals(1, repo.getCourses().size)
    }
    @Test fun rejectedRestoreDoesNotChangeExistingData() {
        val repo = StudyDeskRepository(RuntimeEnvironment.getApplication())
        repo.setDailyNewLimit(9)
        val before = repo.exportFullBackup()
        try { repo.restoreFullBackup(before.replace("\"dailyNewLimit\":9", "\"dailyNewLimit\":900")); fail("must reject") }
        catch (_: IllegalArgumentException) {}
        assertEquals(before, repo.exportFullBackup())
    }
    @Test fun deletedDefaultDoesNotReturnAfterRestart() {
        val context = RuntimeEnvironment.getApplication()
        val repo = StudyDeskRepository(context)
        repo.getCourses().forEach { repo.deleteCourse(it.id) }
        repo.userDb.close()
        assertTrue(StudyDeskRepository(context).getCourses().isEmpty())
    }
}
