package io.github.cuimiles.studydesk.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuimiles.studydesk.core.calendar.*
import io.github.cuimiles.studydesk.core.timetable.*
import io.github.cuimiles.studydesk.data.StudyDeskRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(repository: StudyDeskRepository) {
    val todayDate = remember { today() }
    val actualCurrentWeek = remember { weekOf(todayDate).coerceIn(1, 18) }
    var selectedWeek by remember { mutableStateOf(actualCurrentWeek) }

    var scheduleBlocks by remember { mutableStateOf<List<DisplayGroup>>(emptyList()) }
    var selectionChoices by remember { mutableStateOf<List<Occurrence>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<DisplayGroup?>(null) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    var showManageDialog by remember { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<Course?>(null) }
    var deletingCourse by remember { mutableStateOf<Course?>(null) }
    var reloadTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(selectedWeek, reloadTrigger) {
        scheduleBlocks = repository.getWeekSchedule(selectedWeek)
    }

    val weekDates = remember(selectedWeek) {
        (1..7).map { weekday -> dateOf(selectedWeek, weekday) }
    }
    val hasHolidayInWeek = remember(weekDates) {
        weekDates.any { isHoliday(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第 $selectedWeek 周", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        if (selectedWeek != actualCurrentWeek) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { selectedWeek = actualCurrentWeek },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("回到本周", fontSize = 12.sp)
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { if (selectedWeek > 1) selectedWeek-- }) {
                        Text("◀", fontSize = 16.sp)
                    }
                    IconButton(onClick = { if (selectedWeek < 18) selectedWeek++ }) {
                        Text("▶", fontSize = 16.sp)
                    }
                    TextButton(onClick = { showManageDialog = true }) {
                        Text("管理")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            if (hasHolidayInWeek) {
                Surface(
                    color = Color(0xFFFFF3E0),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "提示：本周包含法定停课日，相应课程默认隐藏。",
                        color = Color(0xFFB78103),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Weekday Headers (7 columns)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 6.dp)
            ) {
                // Period column spacer
                Box(modifier = Modifier.width(36.dp))

                for (i in 0 until 7) {
                    val date = weekDates[i]
                    val isToday = (date == todayDate)
                    val dayName = CalendarConfig.DAYS[i]
                    val shortD = shortDate(date)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = dayName,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = shortD,
                            fontSize = 10.sp,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

            // Timetable Grid (11 periods)
            val rowHeight = 60.dp
            val scrollState = rememberScrollState()

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // Period numbers & times column
                Column(modifier = Modifier.width(36.dp)) {
                    val sampleDate = weekDates[0]
                    val times = timesForDate(sampleDate)
                    for (p in 1..11) {
                        Box(
                            modifier = Modifier
                                .height(rowHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "$p", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                val startTime = times.getOrNull(p - 1)?.substringBefore("-") ?: ""
                                Text(text = startTime, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // 7 Days Grid Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(rowHeight * 11)
                ) {
                    // Horizontal background grid lines
                    for (p in 1..11) {
                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = rowHeight * (p - 1)),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    }

                    // Vertical background grid lines
                    Row(modifier = Modifier.fillMaxSize()) {
                        for (c in 0 until 7) {
                            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                if (c < 6) {
                                    Divider(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(1.dp)
                                            .fillMaxHeight(),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                                    )
                                }
                            }
                        }
                    }

                    // Course Cards
                    for (block in scheduleBlocks) {
                        val occ = block.occurrence
                        val colIndex = weekDates.indexOf(occ.date)
                        if (colIndex !in 0..6) continue

                        val topOffset = rowHeight * (block.start - 1) + 2.dp
                        val blockHeight = rowHeight * (block.end - block.start + 1) - 4.dp

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val colWidth = maxWidth / 7

                            Box(
                                modifier = Modifier
                                    .offset(x = colWidth * colIndex + 1.dp, y = topOffset)
                                    .width(colWidth - 2.dp)
                                    .height(blockHeight)
                                    .background(
                                        Color(block.theme.background),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                                    .clickable {
                                        val overlaps = scheduleBlocks.filter { it.occurrence.date == occ.date && it.start <= block.end && it.end >= block.start }
                                        val choices = overlaps.flatMap { it.members.ifEmpty { listOf(it.occurrence) } }.distinctBy { "${it.course.id}@${it.originalDate}" }
                                        if (choices.size > 1) selectionChoices = choices else selectedGroup = block
                                    }
                                    .padding(4.dp)
                            ) {
                                Column {
                                    Text(
                                        text = occ.course.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(block.theme.text),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = occ.room.ifBlank { "" },
                                        fontSize = 10.sp,
                                        color = Color(block.theme.secondary),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (block.conflicts > 1) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Text(
                                            text = "!",
                                            color = MaterialTheme.colorScheme.onError,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectionChoices.isNotEmpty()) {
        AlertDialog(onDismissRequest = { selectionChoices = emptyList() }, title = { Text("选择课程") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                selectionChoices.forEach { occurrence ->
                    TextButton(onClick = {
                        selectedGroup = DisplayGroup(occurrence, occurrence.sections.min(), occurrence.sections.max())
                        selectionChoices = emptyList()
                    }) { Text("${occurrence.course.name} · ${sectionLabel(occurrence.sections)}节 · ${occurrence.room}") }
                }
            } }, confirmButton = { TextButton(onClick = { selectionChoices = emptyList() }) { Text("关闭") } })
    }
    // Detail Dialog
    selectedGroup?.let { group ->
        val occ = group.occurrence
        val isAdjusted = occ.adjusted
        AlertDialog(
            onDismissRequest = { selectedGroup = null },
            title = { Text(occ.course.name, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("教室: ${occ.room.ifBlank { "未安排" }}", fontSize = 14.sp)
                    Text("节次: 第 ${sectionLabel(occ.sections)} 节", fontSize = 14.sp)
                    Text("时间: ${timeLabel(occ.date, occ.sections)}", fontSize = 14.sp)
                    Text("日期: ${occ.date} (${if (occ.originalDate != occ.date) "原: " + occ.originalDate else "正常周期"})", fontSize = 14.sp)
                    if (occ.course.teachers.isNotEmpty()) {
                        Text("教师: ${occ.course.teachers.joinToString(", ")}", fontSize = 14.sp)
                    }
                    if (occ.course.className.isNotBlank()) {
                        Text("班级: ${occ.course.className}", fontSize = 14.sp)
                    }
                    if (occ.course.note.isNotBlank()) {
                        Text("备注: ${occ.course.note}", fontSize = 14.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showAdjustDialog = true
                }) {
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

    // Adjust Single Occurrence Dialog
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
                    OutlinedTextField(newSections, { newSections = it }, label = { Text("节次（如3-4）") })
                    if (adjustmentError.isNotBlank()) Text(adjustmentError, color = MaterialTheme.colorScheme.error)
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
                    } catch (e: Exception) { adjustmentError = e.message ?: "保存失败" }
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

    // Manage Courses Dialog
    if (showManageDialog) {
        val allCourses = remember(reloadTrigger) { repository.getCourses() }
        AlertDialog(
            onDismissRequest = { showManageDialog = false },
            title = { Text("课程管理 (${allCourses.size} 门)") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Button(
                        onClick = {
                            val supplemented = TimetableEngine.supplementBadminton(allCourses)
                            repository.userDb.clearAndSetCourses(supplemented)
                            reloadTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("补入默认羽毛球课 (周三3-4节)")
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(onClick = { editingCourse = Course(name = "", weekday = 1, sections = listOf(1, 2), weeks = (1..16).toList()) }) { Text("新增课程") }
                    val adjustments = remember(reloadTrigger) { repository.getAdjustments() }
                    for (adjustment in adjustments) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${allCourses.find { it.id == adjustment.courseId }?.name.orEmpty()} · ${adjustment.originalDate} · ${if (adjustment.cancelled) "已取消" else "已调课"}", modifier = Modifier.weight(1f), fontSize = 12.sp)
                            TextButton(onClick = { repository.deleteAdjustment(adjustment.courseId, adjustment.originalDate); reloadTrigger++ }) { Text("恢复") }
                        }
                    }
                    for (c in allCourses) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("周${CalendarConfig.DAYS.getOrElse(c.weekday - 1) { "" }} 第${sectionLabel(c.sections)}节 · ${c.room}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { editingCourse = c }) { Text("编辑") }
                            IconButton(onClick = { deletingCourse = c }) {
                                Text("🗑", fontSize = 14.sp)
                            }
                        }
                        Divider()
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showManageDialog = false }) {
                    Text("完成")
                }
            }
        )
    }
    deletingCourse?.let { course ->
        AlertDialog(onDismissRequest = { deletingCourse = null }, title = { Text("删除 ${course.name}？") },
            text = { Text("同时删除该课程的调课记录。") },
            confirmButton = { TextButton(onClick = { repository.deleteCourse(course.id); deletingCourse = null; reloadTrigger++ }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deletingCourse = null }) { Text("取消") } })
    }
    editingCourse?.let { course ->
        CourseEditor(course, onDismiss = { editingCourse = null }, onSave = { updated ->
            repository.saveCourse(updated)
            editingCourse = null
            reloadTrigger++
        })
    }

}

@Composable
private fun CourseEditor(course: Course, onDismiss: () -> Unit, onSave: (Course) -> Unit) {
    var name by remember(course) { mutableStateOf(course.name) }
    var room by remember(course) { mutableStateOf(course.room) }
    var teachers by remember(course) { mutableStateOf(course.teachers.joinToString(",")) }
    var weekday by remember(course) { mutableStateOf(course.weekday.toString()) }
    var sections by remember(course) { mutableStateOf(course.sections.joinToString(",")) }
    var weeks by remember(course) { mutableStateOf(course.weeks.joinToString(",")) }
    var error by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (course.id.isBlank()) "新增课程" else "编辑课程") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, { name = it }, label = { Text("课程名称") })
            OutlinedTextField(room, { room = it }, label = { Text("地点") })
            OutlinedTextField(teachers, { teachers = it }, label = { Text("教师（逗号分隔）") })
            OutlinedTextField(weekday, { weekday = it }, label = { Text("星期（1–7）") })
            OutlinedTextField(sections, { sections = it }, label = { Text("节次（如 3,4 或 3-4）") })
            OutlinedTextField(weeks, { weeks = it }, label = { Text("周次（如 1-8）") })
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        } }, confirmButton = { TextButton(onClick = {
            try {
                require(name.isNotBlank() && name.length <= 500 && room.length <= 500) { "请输入有效名称和地点" }
                val day = weekday.toInt(); require(day in 1..7) { "星期应为1–7" }
                val updated = course.copy(id = course.id.ifBlank { java.util.UUID.randomUUID().toString() },
                    name = name.trim(), room = room.trim(), teachers = teachers.replace('，', ',').split(',').map { it.trim() }.filter { it.isNotBlank() },
                    weekday = day, sections = parseCourseNumbers(sections, 11), weeks = parseCourseNumbers(weeks, 18))
                onSave(updated)
            } catch (e: Exception) { error = e.message ?: "输入无效" }
        }) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
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
