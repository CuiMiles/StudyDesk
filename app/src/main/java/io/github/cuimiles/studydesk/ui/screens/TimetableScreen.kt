package io.github.cuimiles.studydesk.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuimiles.studydesk.core.calendar.*
import io.github.cuimiles.studydesk.core.timetable.*
import io.github.cuimiles.studydesk.data.StudyDeskRepository
import kotlinx.coroutines.launch

// 小交课表原版色彩风格
private val BgColor = Color(0xFFFCFCFA)
private val TextMainColor = Color(0xFF34423E)
private val TextMutedColor = Color(0xFF949E97)
private val GreenTodayBg = Color(0xFFEAF1E9)
private val GreenTodayText = Color(0xFF426B50)
private val GreenDotColor = Color(0xFF92B39B)
private val GridDividerColor = Color(0xFFF2F3EF)
private val HolidayHintColor = Color(0xFFA49E88)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(
    repository: StudyDeskRepository,
    onNavigateToVocabulary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val todayDate = remember { today() }
    val actualCurrentWeek = remember { weekOf(todayDate).coerceIn(1, 18) }

    val pagerState = rememberPagerState(
        initialPage = (actualCurrentWeek - 1).coerceIn(0, 17),
        pageCount = { 18 }
    )
    val coroutineScope = rememberCoroutineScope()
    val currentWeek = pagerState.currentPage + 1
    val isCurrentWeek = currentWeek == actualCurrentWeek

    var showWeekPicker by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var selectedGroup by remember { mutableStateOf<DisplayGroup?>(null) }
    var selectionChoices by remember { mutableStateOf<List<Occurrence>>(emptyList()) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var deletingCourse by remember { mutableStateOf<Course?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    val weekDates = remember(currentWeek) {
        (1..7).map { weekday -> dateOf(currentWeek, weekday) }
    }
    val currentMonthText = remember(weekDates) {
        "${weekDates[0].substring(5, 7).toInt()}月"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
            .statusBarsPadding()
    ) {
        // 1. 顶部 Header (仿微信小程序原版小交课表)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showWeekPicker = true }
                        .padding(vertical = 2.dp)
                ) {
                    Text(
                        text = "第${currentWeek}周",
                        fontSize = 23.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextMainColor,
                        letterSpacing = (-0.5).sp
                    )
                    if (!isCurrentWeek) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "（非本周）",
                            fontSize = 13.sp,
                            color = TextMutedColor
                        )
                    }
                    Text(
                        text = " ⌄",
                        fontSize = 17.sp,
                        color = Color(0xFF9BA49F),
                        fontWeight = FontWeight.Normal
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "2026-2027秋 · 崔明浩",
                        fontSize = 11.5.sp,
                        color = Color(0xFF939B97)
                    )
                    if (!isCurrentWeek) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = GreenTodayBg,
                            modifier = Modifier.clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(actualCurrentWeek - 1)
                                }
                            }
                        ) {
                            Text(
                                text = "回到本周",
                                fontSize = 11.sp,
                                color = GreenTodayText,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // 顶部右上角快捷操作图标
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateToVocabulary,
                    modifier = Modifier.size(38.dp)
                ) {
                    Text("📖", fontSize = 17.sp)
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.size(38.dp)
                ) {
                    Text("⚙️", fontSize = 17.sp)
                }
                IconButton(
                    onClick = { showManageDialog = true },
                    modifier = Modifier.size(38.dp)
                ) {
                    Text("📋", fontSize = 17.sp)
                }
            }
        }

        // 2. 星期与日期表头 (dayhead)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧月份栏：点击回到本周
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .clickable {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(actualCurrentWeek - 1)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = currentMonthText,
                        fontSize = 12.sp,
                        color = Color(0xFF929B95),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // 7 个星期列 (周一至周日)
            for (i in 0 until 7) {
                val date = weekDates[i]
                val isToday = (date == todayDate)
                val dayName = CalendarConfig.DAYS[i]
                val dayNumber = date.substring(8).toInt().toString()
                val isHoliday = isHoliday(date)
                val isFirstDayOfMonth = date.endsWith("-01")
                val monthHint = if (isFirstDayOfMonth) "${date.substring(5, 7).toInt()}月" else ""

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isToday) GreenTodayBg else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayName,
                        fontSize = 11.5.sp,
                        color = if (isToday) GreenTodayText else Color(0xFF9AA29D),
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayNumber,
                        fontSize = 13.5.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) GreenTodayText else Color(0xFF53635B)
                    )
                    if (isToday) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(GreenDotColor, CircleShape)
                        )
                    } else if (isHoliday) {
                        Text(
                            text = "停课",
                            fontSize = 9.sp,
                            color = HolidayHintColor,
                            lineHeight = 11.sp
                        )
                    } else if (monthHint.isNotEmpty()) {
                        Text(
                            text = monthHint,
                            fontSize = 9.sp,
                            color = HolidayHintColor,
                            lineHeight = 11.sp
                        )
                    } else {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        HorizontalDivider(color = GridDividerColor, thickness = 1.dp)

        // 3. 可左右滑动的多周课表视图 (HorizontalPager 实现平滑手势切周)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { pageIndex ->
            val pageWeek = pageIndex + 1
            val pageDates = remember(pageWeek) {
                (1..7).map { weekday -> dateOf(pageWeek, weekday) }
            }
            val pageBlocks = remember(pageWeek, reloadTrigger) {
                repository.getWeekSchedule(pageWeek)
            }
            // 根据本周首日日期判定夏令时/冬令时
            val pageTimes = remember(pageDates) {
                timesForDate(pageDates[0])
            }
            val rowHeight = 58.dp
            val totalGridHeight = rowHeight * 11
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // 左侧节次与时间刻度 (1-11节)
                    Column(
                        modifier = Modifier
                            .width(42.dp)
                            .height(totalGridHeight)
                    ) {
                        for (p in 1..11) {
                            val timeSpan = pageTimes.getOrNull(p - 1) ?: ""
                            val startTime = timeSpan.substringBefore("-")
                            val endTime = timeSpan.substringAfter("-")

                            Box(
                                modifier = Modifier
                                    .height(rowHeight)
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$p",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFF6C7C73)
                                    )
                                    Text(
                                        text = startTime,
                                        fontSize = 8.5.sp,
                                        color = Color(0xFFB0B7B2),
                                        lineHeight = 10.sp
                                    )
                                    Text(
                                        text = endTime,
                                        fontSize = 8.5.sp,
                                        color = Color(0xFFB0B7B2),
                                        lineHeight = 10.sp
                                    )
                                }
                            }
                        }
                    }

                    // 7 天课表卡片展示区
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .height(totalGridHeight)
                    ) {
                        val colWidth = maxWidth / 7

                        // 绘制背景网格线
                        for (p in 1..11) {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .offset(y = rowHeight * (p - 1)),
                                color = GridDividerColor,
                                thickness = 0.8.dp
                            )
                        }
                        for (c in 1..6) {
                            Box(
                                modifier = Modifier
                                    .offset(x = colWidth * c)
                                    .width(0.8.dp)
                                    .height(totalGridHeight)
                                    .background(GridDividerColor)
                            )
                        }

                        // 渲染课程卡片
                        for (block in pageBlocks) {
                            val occ = block.occurrence
                            val colIndex = pageDates.indexOf(occ.date)
                            if (colIndex !in 0..6) continue

                            val span = block.end - block.start + 1
                            val topOffset = rowHeight * (block.start - 1) + 1.dp
                            val cardHeight = rowHeight * span - 3.dp

                            Box(
                                modifier = Modifier
                                    .offset(x = colWidth * colIndex + 1.dp, y = topOffset)
                                    .width(colWidth - 2.dp)
                                    .height(cardHeight)
                                    .background(
                                        Color(block.theme.background),
                                        shape = RoundedCornerShape(7.dp)
                                    )
                                    .clickable {
                                        val overlaps = pageBlocks.filter {
                                            it.occurrence.date == occ.date && it.start <= block.end && it.end >= block.start
                                        }
                                        val choices = overlaps.flatMap { it.members.ifEmpty { listOf(it.occurrence) } }
                                            .distinctBy { "${it.course.id}@${it.originalDate}" }
                                        if (choices.size > 1) {
                                            selectionChoices = choices
                                        } else {
                                            selectedGroup = block
                                        }
                                    }
                                    .padding(horizontal = 3.dp, vertical = 3.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Text(
                                        text = occ.course.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(block.theme.text),
                                        maxLines = if (span >= 3) 4 else 3,
                                        lineHeight = 12.5.sp,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (occ.room.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = occ.room,
                                            fontSize = 8.8.sp,
                                            color = Color(block.theme.secondary),
                                            maxLines = 2,
                                            lineHeight = 11.sp,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (block.conflicts > 1) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "${block.conflicts}门重叠",
                                            fontSize = 8.sp,
                                            color = Color(0xFFBA1A1A),
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (occ.adjusted) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "已调课",
                                            fontSize = 8.sp,
                                            color = Color(0xFF426B50)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 底部作息温馨提示（特别是 10.1 统一提前半小时）
                if (pageDates.any { it >= "2026-10-01" }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "💡 10月1日起使用冬季作息，下午作息统一提前半小时，详情按当天时间显示",
                            fontSize = 11.sp,
                            color = Color(0xFFA9B0AA),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }

    // 周次选择弹窗 (1-18周快捷直达)
    if (showWeekPicker) {
        AlertDialog(
            onDismissRequest = { showWeekPicker = false },
            title = {
                Text(
                    text = "切换周次",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = TextMainColor
                )
            },
            text = {
                Column {
                    Text(
                        text = "本学期共18周（第17-18周为考试周）",
                        fontSize = 12.sp,
                        color = TextMutedColor,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.height(260.dp)
                    ) {
                        items((1..18).toList()) { w ->
                            val isSelected = w == currentWeek
                            val isActual = w == actualCurrentWeek
                            val isExam = w >= 17

                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = when {
                                    isSelected -> Color(0xFF426B50)
                                    isActual -> GreenTodayBg
                                    else -> Color(0xFFF3F4F1)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(w - 1)
                                        }
                                        showWeekPicker = false
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "第${w}周",
                                        fontWeight = if (isSelected || isActual) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 13.sp,
                                        color = when {
                                            isSelected -> Color.White
                                            isActual -> GreenTodayText
                                            else -> TextMainColor
                                        }
                                    )
                                    if (isExam) {
                                        Text(
                                            text = "考试",
                                            fontSize = 9.5.sp,
                                            color = if (isSelected) Color(0xFFE5EFDF) else Color(0xFFBA1A1A)
                                        )
                                    } else if (isActual) {
                                        Text(
                                            text = "当前",
                                            fontSize = 9.5.sp,
                                            color = GreenTodayText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showWeekPicker = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 多门课程冲突选择弹窗
    if (selectionChoices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { selectionChoices = emptyList() },
            title = { Text("这个时段的课程") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "存在时间重叠，请选择要查看的课程：",
                        fontSize = 12.5.sp,
                        color = TextMutedColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    selectionChoices.forEach { occurrence ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedGroup = DisplayGroup(
                                        occurrence = occurrence,
                                        start = occurrence.sections.minOrNull() ?: 1,
                                        end = occurrence.sections.maxOrNull() ?: 1
                                    )
                                    selectionChoices = emptyList()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F8F6))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = occurrence.course.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextMainColor
                                )
                                Text(
                                    text = "第 ${sectionLabel(occurrence.sections)} 节 · ${occurrence.room.ifBlank { "教室待定" }}",
                                    fontSize = 12.sp,
                                    color = TextMutedColor
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectionChoices = emptyList() }) { Text("关闭") }
            }
        )
    }

    // 课程详情弹窗
    selectedGroup?.let { group ->
        val occ = group.occurrence
        val isAdjusted = occ.adjusted
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = {
                Column {
                    if (isAdjusted) {
                        Surface(
                            color = Color(0xFFFFF3E0),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text(
                                text = "已调课",
                                fontSize = 11.sp,
                                color = Color(0xFFB78103),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = occ.course.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = TextMainColor
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("节次　第 ${sectionLabel(occ.sections)} 节", fontSize = 13.5.sp)
                    Text("时间　${timeLabel(occ.date, occ.sections)}", fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
                    Text("教室　${occ.room.ifBlank { "待定" }}", fontSize = 13.5.sp)
                    Text("班级　${occ.course.className.ifBlank { "未填写" }}", fontSize = 13.5.sp)
                    Text("教师　${occ.course.teachers.joinToString(", ").ifBlank { "未填写" }}", fontSize = 13.5.sp)
                    Text("周次　第 ${occ.course.weeks.joinToString(", ")} 周", fontSize = 13.5.sp)
                    Text("日期　${occ.date} (${if (occ.originalDate != occ.date) "原安排: " + occ.originalDate else "正常周期"})", fontSize = 12.5.sp, color = TextMutedColor)
                    if (occ.course.note.isNotBlank()) {
                        Text("备注　${occ.course.note}", fontSize = 13.sp, color = TextMutedColor)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showAdjustDialog = true }) {
                    Text("调整本次")
                }
            },
            dismissButton = {
                Row {
                    if (isAdjusted) {
                        TextButton(onClick = {
                            repository.deleteAdjustment(occ.course.id, occ.originalDate)
                            selectedGroup = null
                            reloadTrigger++
                        }) {
                            Text("恢复原安排")
                        }
                    }
                    TextButton(onClick = { selectedGroup = null }) {
                        Text("关闭")
                    }
                }
            }
        )
    }

    // 调课弹窗
    if (showAdjustDialog && selectedGroup != null) {
        val occ = selectedGroup!!.occurrence
        var newDate by remember { mutableStateOf(occ.date) }
        var newRoom by remember { mutableStateOf(occ.room) }
        var newSections by remember { mutableStateOf(occ.sections.joinToString(",")) }
        var adjustmentError by remember { mutableStateOf("") }
        var isCancel by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("调整本次课程") },
            text = {
                Column {
                    Text("原日期: ${occ.originalDate}", fontSize = 13.sp)
                    OutlinedTextField(
                        value = newSections,
                        onValueChange = { newSections = it },
                        label = { Text("节次（如3-4）") }
                    )
                    if (adjustmentError.isNotBlank()) {
                        Text(adjustmentError, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newDate,
                        onValueChange = { newDate = it },
                        label = { Text("调整后日期 (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newRoom,
                        onValueChange = { newRoom = it },
                        label = { Text("教室") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isCancel, onCheckedChange = { isCancel = it })
                        Text("取消本次课")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        require(validDate(newDate) && weekOf(newDate) in 1..18) { "日期须在本学期内" }
                        val adj = CourseAdjustment(
                            courseId = occ.course.id,
                            originalDate = occ.originalDate,
                            cancelled = isCancel,
                            date = newDate,
                            sections = parseCourseNumbers(newSections, 11),
                            room = newRoom
                        )
                        repository.saveAdjustment(adj)
                        showAdjustDialog = false
                        selectedGroup = null
                        reloadTrigger++
                    } catch (e: Exception) {
                        adjustmentError = e.message ?: "保存失败"
                    }
                }) {
                    Text("确认保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // 课程管理弹窗
    if (showManageDialog) {
        val allCourses = remember(reloadTrigger) { repository.getCourses() }
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("课程管理 (${allCourses.size} 门)") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FilledTonalButton(
                        onClick = {
                            repository.loadCuiMinghaoCourses(replaceExisting = true)
                            reloadTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔄 恢复/导入崔明浩 2026秋课表 (17门)")
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            editingCourse = Course(
                                name = "",
                                weekday = 1,
                                sections = listOf(1, 2),
                                weeks = (1..16).toList()
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ 手动新增课程")
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("当前课程列表：", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    for (c in allCourses) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text(
                                    "周${CalendarConfig.DAYS.getOrElse(c.weekday - 1) { "" }} 第${sectionLabel(c.sections)}节 · ${c.room.ifBlank { "待定" }}",
                                    fontSize = 11.5.sp,
                                    color = TextMutedColor
                                )
                            }
                            Row {
                                TextButton(onClick = { editingCourse = c }) { Text("编辑", fontSize = 12.sp) }
                                TextButton(onClick = { deletingCourse = c }) {
                                    Text("删除", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showManageDialog = false }) { Text("完成") }
            }
        )
    }

    // 编辑课程弹窗
    editingCourse?.let { course ->
        CourseEditDialog(
            course = course,
            onDismiss = { editingCourse = null },
            onSave = { updated ->
                repository.saveCourse(updated)
                editingCourse = null
                reloadTrigger++
            }
        )
    }

    // 删除课程确认弹窗
    deletingCourse?.let { course ->
        AlertDialog(
            onDismissRequest = { deletingCourse = null },
            title = { Text("确认删除课程") },
            text = { Text("确定要删除「${course.name}」吗？相关的调课记录也将被一并清理。") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.deleteCourse(course.id)
                        deletingCourse = null
                        reloadTrigger++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deletingCourse = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun CourseEditDialog(course: Course, onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember { mutableStateOf(course.name) }
    var room by remember { mutableStateOf(course.room) }
    var teachers by remember { mutableStateOf(course.teachers.joinToString(", ")) }
    var weekday by remember { mutableStateOf(course.weekday.toString()) }
    var sections by remember { mutableStateOf(course.sections.joinToString(",")) }
    var weeks by remember { mutableStateOf(course.weeks.joinToString(",")) }
    var error by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (course.id.isBlank()) "新增课程" else "编辑课程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(room, { room = it }, label = { Text("教室地点") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(teachers, { teachers = it }, label = { Text("教师姓名 (逗号分隔)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(weekday, { weekday = it }, label = { Text("星期 (1–7)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(sections, { sections = it }, label = { Text("节次 (如 1-2 或 3,4)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(weeks, { weeks = it }, label = { Text("周次 (如 1-8 或 1-16)") }, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                try {
                    require(name.isNotBlank() && name.length <= 500 && room.length <= 500) { "请输入有效名称和地点" }
                    val day = weekday.toInt()
                    require(day in 1..7) { "星期应为1–7" }
                    val updated = course.copy(
                        id = course.id.ifBlank { java.util.UUID.randomUUID().toString() },
                        name = name.trim(),
                        room = room.trim(),
                        teachers = teachers.replace('，', ',').split(',').map { it.trim() }.filter { it.isNotBlank() },
                        weekday = day,
                        sections = parseCourseNumbers(sections, 11),
                        weeks = parseCourseNumbers(weeks, 18)
                    )
                    onSave(updated)
                } catch (e: Exception) {
                    error = e.message ?: "输入无效"
                }
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun parseCourseNumbers(text: String, max: Int): List<Int> {
    val numbers = text.replace('，', ',').split(',').flatMap { token ->
        val parts = token.trim().split('-')
        require(parts.size in 1..2) { "请输入数字或区间" }
        val start = parts[0].trim().toInt()
        val end = if (parts.size == 2) parts[1].trim().toInt() else start
        require(start in 1..max && end in start..max) { "范围须在1–${max}以内" }
        (start..end).toList()
    }.distinct().sorted()
    require(numbers.isNotEmpty()) { "不能为空" }
    return numbers
}
