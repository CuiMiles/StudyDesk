package io.github.cuimiles.studydesk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuimiles.studydesk.core.calendar.*
import io.github.cuimiles.studydesk.core.timetable.DisplayGroup
import io.github.cuimiles.studydesk.data.StudyDeskRepository
import io.github.cuimiles.studydesk.ui.navigation.Screen

@Composable
fun DeskScreen(
    repository: StudyDeskRepository,
    onNavigate: (Screen) -> Unit
) {
    val todayDate = remember { today() }
    val currentWeek = remember { weekOf(todayDate).coerceIn(1, 18) }
    val currentWeekday = remember { weekdayOf(todayDate) }
    val dayName = remember { CalendarConfig.DAYS.getOrElse(currentWeekday - 1) { "" } }

    var todayClasses by remember { mutableStateOf<List<DisplayGroup>>(emptyList()) }
    var vocabStats by remember { mutableStateOf<Pair<Int, Int>>(Pair(0, 0)) }

    LaunchedEffect(Unit) {
        val weekBlocks = repository.getWeekSchedule(currentWeek)
        todayClasses = weekBlocks.filter { it.occurrence.date == todayDate }

        val vStore = repository.loadTodaySession()
        val queue = vStore.session?.queue ?: emptyList()
        val dueCount = queue.count { it.mode.name != "NEW" }
        val newCount = queue.count { it.mode.name == "NEW" }
        vocabStats = Pair(newCount, dueCount)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Date & Week Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "第 $currentWeek 周 · 星期$dayName",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$todayDate  (西安交通大学 2026-2027秋季学期)",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Today's Classes Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日课程",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { onNavigate(Screen.TIMETABLE) }) {
                        Text("完整课表")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (todayClasses.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "今日无课，自由自习或休息吧 ☕",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    for (block in todayClasses) {
                        val occ = block.occurrence
                        val timeStr = timeLabel(occ.date, occ.sections)
                        val secStr = sectionLabel(occ.sections)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .background(
                                    Color(block.theme.background),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = occ.course.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(block.theme.text)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "第 $secStr 节 ($timeStr) · ${occ.room.ifBlank { "未排教室" }}",
                                    fontSize = 13.sp,
                                    color = Color(block.theme.secondary)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vocabulary Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "今日背词",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${vocabStats.second}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = "待复习", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${vocabStats.first}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = "今日新学", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { onNavigate(Screen.VOCABULARY) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("前往背词")
                }
            }
        }
    }
}
