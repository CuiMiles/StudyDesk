package io.github.cuimiles.studydesk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.github.cuimiles.studydesk.core.fivestep.FiveStepPayload
import io.github.cuimiles.studydesk.core.vocabulary.*
import io.github.cuimiles.studydesk.data.StudyDeskRepository
import kotlinx.serialization.json.Json

enum class VocabSubTab(val title: String) {
    DAILY("今日背词"),
    CHAPTERS("章节浏览"),
    SPELLING("拼写练习"),
    FAVORITES("熟词与收藏")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(repository: StudyDeskRepository) {
    var selectedTab by remember { mutableStateOf(VocabSubTab.DAILY) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            VocabSubTab.values().forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.title, fontSize = 13.sp) }
                )
            }
        }

        when (selectedTab) {
            VocabSubTab.DAILY -> DailyStudyTab(repository)
            VocabSubTab.CHAPTERS -> ChaptersTab(repository)
            VocabSubTab.SPELLING -> SpellingTab(repository)
            VocabSubTab.FAVORITES -> FavoritesAndMasteredTab(repository)
        }
    }
}

@Composable
fun DailyStudyTab(repository: StudyDeskRepository) {
    var store by remember { mutableStateOf(repository.loadTodaySession()) }
    val queue = store.session?.queue ?: emptyList()
    val currentAttempt = queue.firstOrNull()

    if (currentAttempt == null) {
        // Study Completed State
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉 今日计划已完成！", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "到期复习与新词额度均已学完。温故而知新，明日继续保持！",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        store = repository.loadTodaySession()
                    }) {
                        Text("刷新队列")
                    }
                }
            }
        }
        return
    }

    val word = remember(currentAttempt.id) { repository.contentDb.getWord(currentAttempt.entryId) }
    val senses = remember(currentAttempt.id) { repository.contentDb.getSenses(currentAttempt.entryId) }
    val generation = remember(currentAttempt.id) { repository.contentDb.getGeneration(currentAttempt.entryId) }
    val isFav = store.favorites.contains(currentAttempt.entryId)

    var showChinese by remember(currentAttempt.id) { mutableStateOf(false) }
    var selectedSenseIndex by remember(currentAttempt.id) { mutableStateOf(0) }
    var showDeepExpanded by remember(currentAttempt.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Progress and quick actions header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val modeLabel = when (currentAttempt.mode) {
                AttemptMode.NEW -> "新学"
                AttemptMode.REVIEW -> "复习"
                AttemptMode.RETRY -> "重试"
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "$modeLabel · 队列余 ${queue.size} 词",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Row {
                IconButton(onClick = {
                    store = repository.toggleFavorite(currentAttempt.entryId)
                }) {
                    Text(if (isFav) "★" else "☆", fontSize = 20.sp, color = if (isFav) Color(0xFFE6A23C) else Color.Gray)
                }
                OutlinedButton(
                    onClick = {
                        store = repository.markMastered(currentAttempt.entryId)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("熟 (跳过)", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Word Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Headword & Pronunciation
                Text(
                    text = word?.headword ?: currentAttempt.entryId,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (word?.pronunciation?.isNotBlank() == true) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = word.pronunciation,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Sense Selector if multiple
                if (senses.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSenseIndex,
                        edgePadding = 0.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        senses.forEachIndexed { idx, s ->
                            Tab(
                                selected = selectedSenseIndex == idx,
                                onClick = { selectedSenseIndex = idx },
                                text = { Text("${s.pos} · ${idx + 1}", fontSize = 12.sp) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Current Sense Definition (English First)
                val currentSense = senses.getOrNull(selectedSenseIndex)
                if (currentSense != null) {
                    Text(
                        text = "[${currentSense.pos}] ${currentSense.definitionEn}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 22.sp
                    )

                    if (currentSense.examples.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        for (eg in currentSense.examples) {
                            Text(
                                text = "• $eg",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    if (currentSense.synonyms.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Synonyms: " + currentSense.synonyms.joinToString(", "),
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    Text("收录释义查无词条", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Hidden Chinese Definition Toggle
                TextButton(
                    onClick = { showChinese = !showChinese },
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (showChinese) "隐藏中文释义" else "显示中文释义", fontSize = 13.sp)
                }
                if (showChinese) {
                    val entry = remember(currentAttempt.entryId) {
                        repository.contentDb.getEntriesByChapter(1).find { it.wordId == currentAttempt.entryId }
                    }
                    Text(
                        text = entry?.glossZh ?: "暂无中文词头注释",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 5-Step Deep Analysis Section
                if (generation != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeepExpanded = !showDeepExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("五步深度解析", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                        Text(if (showDeepExpanded) "收起 ▲" else "展开 ▼", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    if (showDeepExpanded) {
                        val payload = try {
                            Json { ignoreUnknownKeys = true }.decodeFromString<FiveStepPayload>(generation.payloadJson)
                        } catch (e: Exception) { null }

                        if (payload != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            StepItem("1. 具体画面", payload.concreteImage)
                            StepItem("2. 近义词对比", payload.synonymsComparison)
                            StepItem("3. 语域及语境", payload.registerAndContexts)
                            StepItem("4. 典型搭配", payload.collocations)
                            StepItem("5. 联想概念", payload.associations)
                            StepItem("6. 综合示例", payload.integratedExample)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Buttons: 3 feedback buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    store = repository.rateWord(currentAttempt.id, Rating.UNKNOWN)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("不认识", fontSize = 15.sp)
            }

            Button(
                onClick = {
                    store = repository.rateWord(currentAttempt.id, Rating.FUZZY)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD35400)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("模糊", fontSize = 15.sp)
            }

            Button(
                onClick = {
                    store = repository.rateWord(currentAttempt.id, Rating.KNOWN)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF27AE60)),
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("认识", fontSize = 15.sp)
            }
        }
    }
}

@Composable
fun StepItem(title: String, content: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Text(text = content, fontSize = 13.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ChaptersTab(repository: StudyDeskRepository) {
    val chapters = remember { repository.contentDb.getChapters() }
    var selectedChapter by remember { mutableStateOf<Int?>(null) }

    if (selectedChapter == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text("IELTS Word List (共 48 单元)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(4.dp))
            }
            items(chapters) { ch ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { selectedChapter = ch },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Word List $ch", fontWeight = FontWeight.Medium)
                        Text("查看词条 ➔", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    } else {
        val entries = remember(selectedChapter) { repository.contentDb.getEntriesByChapter(selectedChapter!!) }
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedChapter = null }) {
                    Text("⬅ 返回目录")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Word List $selectedChapter (${entries.size} 词)", fontWeight = FontWeight.Bold)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(entries) { e ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.original.substringBefore(" "), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(e.sourcePronunciation, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(e.glossZh, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpellingTab(repository: StudyDeskRepository) {
    val store = remember { repository.getVocabularyStore() }
    val allIds = remember { repository.contentDb.getAllWordIds().shuffled().take(50) }
    var currentIndex by remember { mutableStateOf(0) }
    var userInput by remember { mutableStateOf("") }
    var checkResult by remember { mutableStateOf<Boolean?>(null) }

    val currentWordId = allIds.getOrNull(currentIndex)
    val word = remember(currentWordId) { currentWordId?.let { repository.contentDb.getWord(it) } }
    val senses = remember(currentWordId) { currentWordId?.let { repository.contentDb.getSenses(it) } }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("拼写练习", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        if (word != null && senses != null && senses.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "英文释义提示：",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "[${senses[0].pos}] ${senses[0].definitionEn}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = userInput,
                onValueChange = {
                    userInput = it
                    checkResult = null
                },
                label = { Text("输入英文单词") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            checkResult?.let { passed ->
                if (passed) {
                    Text("✓ 拼写正确！", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Text("✗ 拼写错误，正确答案是: ${word.headword}", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        val correct = VocabularyEngine.checkSpelling(userInput, word.headword)
                        checkResult = correct
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("校验拼写")
                }

                OutlinedButton(
                    onClick = {
                        userInput = ""
                        checkResult = null
                        if (currentIndex < allIds.size - 1) currentIndex++ else currentIndex = 0
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("下一个")
                }
            }
        }
    }
}

@Composable
fun FavoritesAndMasteredTab(repository: StudyDeskRepository) {
    var store by remember { mutableStateOf(repository.getVocabularyStore()) }
    var tabIndex by remember { mutableStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("熟词本") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("生词收藏") })
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (tabIndex == 0) {
            val familiarIds = store.progress.filter { it.value.state == ProgressState.FAMILIAR }.keys.toList()
            if (familiarIds.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("熟词本为空。遇到已掌握词汇点击“熟”即可加入。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(familiarIds) { wid ->
                        val word = remember(wid) { repository.contentDb.getWord(wid) }
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word?.headword ?: wid, fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    store = repository.relearn(wid)
                                }) {
                                    Text("重新学习")
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val favIds = store.favorites
            if (favIds.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无收藏词汇。学习时点击五角星可收藏难词。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(favIds) { wid ->
                        val word = remember(wid) { repository.contentDb.getWord(wid) }
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(word?.headword ?: wid, fontWeight = FontWeight.Bold)
                                TextButton(onClick = {
                                    store = repository.toggleFavorite(wid)
                                }) {
                                    Text("取消收藏")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
