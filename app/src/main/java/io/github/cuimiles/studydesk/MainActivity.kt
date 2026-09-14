package io.github.cuimiles.studydesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.cuimiles.studydesk.data.StudyDeskRepository
import io.github.cuimiles.studydesk.ui.navigation.Screen
import io.github.cuimiles.studydesk.ui.screens.DeskScreen
import io.github.cuimiles.studydesk.ui.screens.SettingsScreen
import io.github.cuimiles.studydesk.ui.screens.TimetableScreen
import io.github.cuimiles.studydesk.ui.screens.VocabularyScreen
import io.github.cuimiles.studydesk.ui.theme.StudyDeskTheme

class MainActivity : ComponentActivity() {

    private lateinit var repository: StudyDeskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = StudyDeskRepository(applicationContext)

        setContent {
            StudyDeskTheme {
                var currentScreen by remember { mutableStateOf(Screen.DESK) }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            Screen.values().forEach { screen ->
                                NavigationBarItem(
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen },
                                    label = { Text(screen.title, fontSize = 12.sp, fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Normal) },
                                    icon = {
                                        val iconText = when (screen) {
                                            Screen.DESK -> "🏠"
                                            Screen.TIMETABLE -> "📅"
                                            Screen.VOCABULARY -> "📖"
                                            Screen.SETTINGS -> "⚙️"
                                        }
                                        Text(iconText, fontSize = 18.sp)
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        when (currentScreen) {
                            Screen.DESK -> DeskScreen(repository = repository, onNavigate = { currentScreen = it })
                            Screen.TIMETABLE -> TimetableScreen(repository = repository)
                            Screen.VOCABULARY -> VocabularyScreen(repository = repository)
                            Screen.SETTINGS -> SettingsScreen(repository = repository)
                        }
                    }
                }
            }
        }
    }
}
