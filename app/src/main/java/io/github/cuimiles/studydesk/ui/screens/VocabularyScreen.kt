package io.github.cuimiles.studydesk.ui.screens

import io.github.cuimiles.studydesk.R
import io.github.cuimiles.studydesk.ui.components.*
import androidx.compose.ui.res.painterResource
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
    FAVORITES("熟词与收藏"),
    DIFFICULT("重难点")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VocabularyScreen(repository: StudyDeskRepository) {
    var selectedTab by remember { mutableStateOf(VocabSubTab.DAILY) }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = selectedTab.ordinal) {
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
            VocabSubTab.DIFFICULT -> DifficultyTab(repository)
        }
    }
}

@Composable
fun DailyStudyTab(repository: StudyDeskRepository) {
    var store by remember { mutableStateOf(repository.loadTodaySession()) }
    val answered = store.pendingAnswer
    val attempt = answered?.attempt ?: store.session?.queue?.firstOrNull()
    if (attempt == null) {
        Column(Modifier.padding(24.dp)) {
            Text("今日计划已完成", fontSize = 22.sp)
            Button(onClick = { store = repository.loadTodaySession() }) { Text("刷新") }
        }
        return
    }
    val word = remember(attempt.entryId) { repository.contentDb.getWord(attempt.entryId) } ?: return
    val senses = remember(attempt.entryId) { repository.contentDb.getSenses(attempt.entryId) }
    val forms = remember(word) { (listOf(word.headword, word.lookup) + word.forms).distinct() }
    val examples = remember(senses) { senses.flatMap { it.examples }.distinct().filter { ExampleText.matches(it, forms).isNotEmpty() } }
    var showAll by remember(attempt.id) { mutableStateOf(false) }
    var showChinese by remember(attempt.id) { mutableStateOf(false) }
    val progress = store.progress[attempt.entryId]
    val scroll = rememberScrollState()
    LaunchedEffect(attempt.id, answered != null, showChinese) { scroll.scrollTo(0) }
    Column(Modifier.fillMaxSize()) {
      Column(Modifier.weight(1f).verticalScroll(scroll).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.headword, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
            Spacer(Modifier.width(8.dp))
            RecognitionDots(progress?.recognition ?: 0)
            if (progress?.difficult == true) {
                Spacer(Modifier.width(8.dp))
                Icon(painterResource(R.drawable.ic_alert), contentDescription = "重难点词", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
            if (answered == null) TextButton(onClick = { store = repository.markMastered(attempt.entryId) }) { Text("熟") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.pronunciation)
            io.github.cuimiles.studydesk.ui.components.WordAudio(word.headword, attempt.id,
                alreadyPlayed = { repository.getVocabularyStore().autoSpokenAttemptId == attempt.id },
                onAutoPlayed = { repository.markAutoSpoken(attempt.id) })
        }
        if (answered == null) {
            if (examples.isEmpty()) Text("暂无例句")
            (if (showAll) examples else examples.take(3)).forEach { HighlightedExample(it, forms) }
            if (examples.size > 3) IconButton(onClick = { showAll = !showAll }) {
                Icon(painterResource(if (showAll) R.drawable.ic_collapse else R.drawable.ic_expand), contentDescription = if (showAll) "收起例句" else "更多例句")
            }
        } else {
            if (showChinese) Text(repository.contentDb.getChineseGloss(word.id).ifBlank { "暂无中文提示" }, modifier = Modifier.padding(vertical = 8.dp))
            val generation = remember(attempt.entryId) { repository.contentDb.getGeneration(attempt.entryId) }
            senses.forEachIndexed { index, sense ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${index + 1}. [${sense.pos}] ${sense.definitionEn}", fontWeight = FontWeight.SemiBold)
                        sense.examples.forEach { example ->
                            HighlightedExample(example, forms)
                        }
                        if (sense.synonyms.isNotEmpty()) Text("Synonyms: ${sense.synonyms.joinToString()}")
                        if (generation?.senseId == sense.id) {
                            val payload = remember(generation) { runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<FiveStepPayload>(generation.payloadJson) }.getOrNull() }
                            var expanded by remember(attempt.id, sense.id) { mutableStateOf(false) }
                            if (payload != null) {
                                TextButton(onClick = { expanded = !expanded }) { Text("五步解析") }
                                if (expanded) {
                                    StepItem("1. 画面锚定", payload.concreteImage)
                                    StepItem("2. 语义场对比", payload.synonymsComparison)
                                    StepItem("3. 语域", payload.registerAndContexts)
                                    StepItem("4. 语义韵", payload.collocations)
                                    StepItem("5. 联想网络", payload.associations)
                                    StepItem("综合示例", payload.integratedExample)
                                    StepItem("说明", payload.integratedExampleMapping)
                                }
                            }
                        }
                    }
                }
            }
            if (senses.isEmpty()) Text("英文释义待补全")

        }
      }
      Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
          FavoriteIcon(word.id in store.favorites) { store = repository.toggleFavorite(word.id) }
          if (answered != null) {
              ChineseIcon(showChinese) { showChinese = !showChinese }
              Spacer(Modifier.weight(1f))
              Button(onClick = { store = repository.nextWord() }) { Text("下一词") }
          } else {
              OutlinedButton(onClick = { store = repository.rateWord(attempt.id, Rating.UNKNOWN) }, modifier = Modifier.weight(1f)) { Text("不认识") }
              Button(onClick = { store = repository.rateWord(attempt.id, Rating.KNOWN) }, modifier = Modifier.weight(1f)) { Text("认识") }
          }
      }
    }
}

@Composable
fun HighlightedExample(text: String, forms: List<String>) {
    val annotated = remember(text, forms) {
        androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            ExampleText.matches(text, forms).forEach { range ->
                addStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
            }
        }
    }
    Text(annotated, modifier = Modifier.padding(vertical = 6.dp), lineHeight = 23.sp)
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
    var query by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("搜索单词") }, modifier = Modifier.fillMaxWidth())
        if (query.isNotBlank()) {
            val words = remember(query) { repository.contentDb.searchWords(query) }
            LazyColumn { items(words, key = { it.id }) { WordBrowserCard(repository, it.id, "") } }
        } else if (selectedChapter == null) {
            Text("IELTS Word List · ${chapters.size} 单元", fontWeight = FontWeight.Bold)
            LazyColumn { items(chapters) { chapter ->
                TextButton(onClick = { selectedChapter = chapter }) { Text("Word List $chapter") }
            } }
        } else {
            TextButton(onClick = { selectedChapter = null }) { Text("返回目录 · Word List $selectedChapter") }
            val entries = remember(selectedChapter) { repository.contentDb.getEntriesByChapter(selectedChapter!!) }
            LazyColumn { items(entries, key = { it.position }) { entry ->
                WordBrowserCard(repository, entry.wordId, entry.glossZh)
            } }
        }
    }
}

@Composable
fun WordBrowserCard(repository: StudyDeskRepository, wordId: String, chinese: String) {
    val word = remember(wordId) { repository.contentDb.getWord(wordId) } ?: return
    val senses = remember(wordId) { repository.contentDb.getSenses(wordId) }
    var showChinese by remember(wordId) { mutableStateOf(false) }
    var senseIndex by remember(wordId) { mutableStateOf(0) }
    var store by remember(wordId) { mutableStateOf(repository.getVocabularyStore()) }
    var showQuestion by remember(wordId) { mutableStateOf(false) }
    var question by remember(wordId) { mutableStateOf("") }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(word.headword, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                RecognitionDots(store.progress[wordId]?.recognition ?: 0)
            }
            Text(word.pronunciation)
            val sense = senses.getOrNull(senseIndex)
            Text(sense?.let { "[${it.pos}] ${it.definitionEn}" } ?: "English definition pending")
            if (senses.size > 1) TextButton(onClick = { senseIndex = (senseIndex + 1) % senses.size }) { Text("${senseIndex + 1}/${senses.size} ›") }
            if (showChinese) Text(chinese.ifBlank { repository.contentDb.getChineseGloss(wordId) })
            Row {
                TextButton(onClick = {
                    store = if (store.progress[wordId]?.state == ProgressState.FAMILIAR) repository.relearn(wordId) else repository.markMastered(wordId)
                }) { Text(if (store.progress[wordId]?.state == ProgressState.FAMILIAR) "重学" else "熟") }
                ChineseIcon(showChinese) { showChinese = !showChinese }
                FavoriteIcon(wordId in store.favorites) { store = repository.toggleFavorite(wordId) }
                IconButton(onClick = { showQuestion = !showQuestion }) { Icon(painterResource(R.drawable.ic_question), contentDescription = "提问") }
            }
            if (showQuestion) {
            OutlinedTextField(value = question, onValueChange = { question = it.take(2000) }, label = { Text("关于这个词的问题") })
            TextButton(enabled = question.isNotBlank(), onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString("Word: ${word.headword}\nDefinition: ${sense?.definitionEn.orEmpty()}\nQuestion: $question"))
            }) { Text("复制") }
            }
        }
    }
}

@Composable
fun SpellingTab(repository: StudyDeskRepository) {
    val store = remember { repository.getVocabularyStore() }
    val allIds = remember { repository.contentDb.getAllWordIds().filter { store.progress[it]?.state != ProgressState.FAMILIAR && repository.contentDb.getSenses(it).isNotEmpty() }.shuffled().take(50) }
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

        if (allIds.isEmpty()) Text("暂无可练习的单词")
        if (word != null && senses != null && senses.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                    Text("✓ 正确", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                } else {
                    Text("✗ ${word.headword}", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        if (checkResult != null) return@Button
                        val correct = VocabularyEngine.checkSpelling(userInput, word.headword)
                        repository.recordSpelling(word.id, correct)
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
                    Text("暂无收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                FavoriteIcon(selected = true) {
                                    store = repository.toggleFavorite(wid)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DifficultyTab(repository: StudyDeskRepository) {
    val store = remember { repository.getVocabularyStore() }
    var level by remember { mutableStateOf(-1) }
    Column(Modifier.padding(12.dp)) {
        ScrollableTabRow(selectedTabIndex = level + 1) {
            listOf("重难点", "陌生", "初识", "渐熟", "长期").forEachIndexed { i, title ->
                Tab(selected = level == i - 1, onClick = { level = i - 1 }, text = { Text(title) })
            }
        }
        val ids = store.progress.filter { (_, p) -> p.state != ProgressState.FAMILIAR && if (level == -1) p.difficult else p.recognition == level }
        Text("${ids.size} 词")
        LazyColumn { items(ids.keys.toList()) { id ->
            WordBrowserCard(repository, id, repository.contentDb.getChineseGloss(id))
        } }
    }
}
