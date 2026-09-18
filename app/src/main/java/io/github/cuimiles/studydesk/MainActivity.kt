package io.github.cuimiles.studydesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import io.github.cuimiles.studydesk.data.StudyDeskRepository
import io.github.cuimiles.studydesk.ui.navigation.Screen
import io.github.cuimiles.studydesk.ui.screens.SettingsScreen
import io.github.cuimiles.studydesk.ui.screens.TimetableScreen
import io.github.cuimiles.studydesk.ui.screens.VocabularyScreen
import io.github.cuimiles.studydesk.ui.theme.StudyDeskTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: StudyDeskRepository

    override fun onDestroy() {
        repository.contentDb.close()
        repository.userDb.close()
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = StudyDeskRepository(applicationContext)

        setContent {
            StudyDeskTheme {
                // 默认一进主页就展示课表功能，彻底移除底部导航栏占用空间
                var currentScreen by rememberSaveable { mutableStateOf(Screen.TIMETABLE) }

                androidx.activity.compose.BackHandler(enabled = currentScreen != Screen.TIMETABLE) {
                    currentScreen = Screen.TIMETABLE
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    when (currentScreen) {
                        Screen.TIMETABLE, Screen.DESK -> TimetableScreen(
                            repository = repository,
                            onNavigateToVocabulary = { currentScreen = Screen.VOCABULARY },
                            onNavigateToSettings = { currentScreen = Screen.SETTINGS }
                        )
                        Screen.VOCABULARY -> VocabularyScreen(
                            repository = repository,
                            onBack = { currentScreen = Screen.TIMETABLE }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            repository = repository,
                            onBack = { currentScreen = Screen.TIMETABLE }
                        )
                    }
                }
            }
        }
    }
}
