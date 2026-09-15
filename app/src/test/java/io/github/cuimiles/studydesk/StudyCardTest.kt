package io.github.cuimiles.studydesk

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.cuimiles.studydesk.data.StudyDeskRepository
import io.github.cuimiles.studydesk.ui.screens.DailyStudyTab
import io.github.cuimiles.studydesk.ui.theme.StudyDeskTheme
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun definitionHiddenUntilJudgmentAndNextIsExplicit() {
        val repo = StudyDeskRepository(RuntimeEnvironment.getApplication())
        val attempt = repo.loadTodaySession().session!!.queue.first()
        val word = repo.contentDb.getWord(attempt.entryId)!!
        val definition = repo.contentDb.getSenses(word.id).first().definitionEn
        compose.setContent { StudyDeskTheme { DailyStudyTab(repo) } }
        compose.onNodeWithText(word.headword).assertIsDisplayed()
        compose.onAllNodesWithText(definition, substring = true).assertCountEquals(0)
        compose.onNodeWithText("认识", substring = false).performScrollTo().performClick()
        compose.onNodeWithText(word.headword).assertIsDisplayed()
        compose.onAllNodesWithText(definition, substring = true).assertCountEquals(1)
        compose.onNodeWithText("下一词").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNull(repo.getVocabularyStore().pendingAnswer)
            assertEquals(1, repo.getVocabularyStore().progress[word.id]!!.recognition)
        }
    }
}
