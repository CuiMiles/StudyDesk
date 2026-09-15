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
        compose.onAllNodesWithText("认知", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("先根据例句", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("收藏").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("取消收藏").assertIsDisplayed()
        compose.onNodeWithText("认识", substring = false).assertIsDisplayed().performClick()
        compose.onNodeWithText(word.headword).assertIsDisplayed()
        compose.onAllNodesWithText(definition, substring = true).assertCountEquals(1)
        compose.onAllNodesWithText("下次复习", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("历史语料", substring = true).assertCountEquals(0)
        compose.onAllNodesWithText("Open English Wordnet", substring = true).assertCountEquals(0)
        compose.onNodeWithContentDescription("显示中文").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("隐藏中文").assertIsDisplayed()
        compose.onNodeWithText(repo.contentDb.getChineseGloss(word.id)).assertExists()
        compose.onNodeWithContentDescription("隐藏中文").performClick()
        compose.onNodeWithText("下一词").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertNull(repo.getVocabularyStore().pendingAnswer)
            assertEquals(1, repo.getVocabularyStore().progress[word.id]!!.recognition)
        }
    }
}
