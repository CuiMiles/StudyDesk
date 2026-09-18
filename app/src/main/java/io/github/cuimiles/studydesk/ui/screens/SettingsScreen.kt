package io.github.cuimiles.studydesk.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.serialization.json.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cuimiles.studydesk.data.StudyDeskRepository

@Composable
fun SettingsScreen(
    repository: StudyDeskRepository,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var dailyLimit by remember { mutableStateOf(repository.getVocabularyStore().settings.dailyNewLimit) }

    var showExportDialog by remember { mutableStateOf(false) }
    var exportJsonText by remember { mutableStateOf("") }

    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }
    var supplementBadminton by remember { mutableStateOf(true) }

    var showConfirmClear1 by remember { mutableStateOf(false) }
    var showConfirmClear2 by remember { mutableStateOf(false) }

    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(repository.exportFullBackup()) }
                ?: error("无法打开文件")
            Toast.makeText(context, "备份已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) { Toast.makeText(context, "导出失败：${e.message}", Toast.LENGTH_LONG).show() }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited() } ?: error("无法读取文件")
            importJsonText = bytes.toString(Charsets.UTF_8)
            Json.parseToJsonElement(importJsonText).jsonObject
            showImportDialog = true
        } catch (e: Exception) { Toast.makeText(context, "读取失败：${e.message}", Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (onBack != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("返回课表", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text("设置与数据", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        UsageGuide()
        Spacer(modifier = Modifier.height(16.dp))

        // Daily Limit Setting
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("每日新词额度: $dailyLimit 词", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = dailyLimit.toFloat(),
                    onValueChange = { dailyLimit = it.toInt() },
                    onValueChangeFinished = { repository.setDailyNewLimit(dailyLimit) },
                    valueRange = 0f..100f,
                    steps = 99
                )
                Text(
                    "设为 0 时暂停学习新词，已到期的复习单词依然会正常推送。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ReviewSettings(repository)
        Spacer(Modifier.height(16.dp))

        // Timetable Backup & Restore
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("全部个人数据备份与恢复", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            exportFile.launch("StudyDesk-backup.json")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出备份")
                    }

                    OutlinedButton(
                        onClick = {
                            importFile.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("选择文件")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Danger Zone: Clear Data
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("危险区域", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showConfirmClear1 = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("清空所有个人数据")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // About & License
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于 StudyDesk", fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                Text("版本: v0.2.1", fontSize = 13.sp)
                Text("词典: Open English Wordnet 2025 (CC BY 4.0)；补充例句/频次: Princeton WordNet 3.0", fontSize = 13.sp)
                Text("词表来源: IELTS Word List (3611 条用户授权词表)", fontSize = 13.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "本产品为非官方个人学习工具，离线保护隐私，不收集个人数据与设备信息。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Export Dialog
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("完整备份 JSON") },
            text = {
                Column {
                    Text("复制下方 JSON 文本即可妥善备份课程与调课记录：", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportJsonText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth().height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("StudyDesk_Backup", exportJsonText))
                    Toast.makeText(context, "备份已复制到剪贴板", Toast.LENGTH_SHORT).show()
                    showExportDialog = false
                }) {
                    Text("复制到剪贴板")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) { Text("关闭") }
            }
        )
    }

    // Import Dialog
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("导入课表 / 恢复备份") },
            text = {
                Column {
                    Text("确认导入下方文件：完整备份将替换全部个人数据；旧课表仅替换课程及调课，保留背词记录。", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonText,
                        onValueChange = { importJsonText = it },
                        placeholder = { Text("粘贴 JSON...") },
                        modifier = Modifier.fillMaxWidth().height(160.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = supplementBadminton, onCheckedChange = { supplementBadminton = it })
                        Text("自动补入默认羽毛球课 (若缺失)", fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    try {
                        val root = Json.parseToJsonElement(importJsonText).jsonObject
                        if (root.containsKey("backupVersion") || root.containsKey("workspaceVersion")) {
                            repository.restoreFullBackup(importJsonText)
                            Toast.makeText(context, "完整备份已成功恢复", Toast.LENGTH_SHORT).show()
                        } else {
                            val count = repository.importTimetable(importJsonText, supplementBadminton)
                            Toast.makeText(context, "成功导入 $count 门课程", Toast.LENGTH_SHORT).show()
                        }
                        dailyLimit = repository.getVocabularyStore().settings.dailyNewLimit
                        showImportDialog = false
                    } catch (e: Exception) {
                        Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }) {
                    Text("确认替换并导入")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("取消") }
            }
        )
    }

    // Confirmation 1
    if (showConfirmClear1) {
        AlertDialog(
            onDismissRequest = { showConfirmClear1 = false },
            title = { Text("确定清空数据？") },
            text = { Text("将清除本地存储的所有课程、调课记录、背词进度和收藏。") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClear1 = false
                        showConfirmClear2 = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("下一步确认")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear1 = false }) { Text("取消") }
            }
        )
    }

    // Confirmation 2
    if (showConfirmClear2) {
        AlertDialog(
            onDismissRequest = { showConfirmClear2 = false },
            title = { Text("再次确认清空") },
            text = { Text("此操作不可恢复！请确认是否清空所有个人数据？") },
            confirmButton = {
                Button(
                    onClick = {
                        repository.clearAllUserData()
                        dailyLimit = 20
                        showConfirmClear2 = false
                        Toast.makeText(context, "个人数据已清空", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("彻底清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClear2 = false }) { Text("取消") }
            }
        )
    }
}

private fun java.io.InputStream.readBytesLimited(): ByteArray {
    val result = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val n = read(buffer)
        if (n < 0) break
        require(result.size() + n <= 20_000_000) { "文件超过20MB" }
        result.write(buffer, 0, n)
    }
    return result.toByteArray()
}

@Composable
private fun ReviewSettings(repository: StudyDeskRepository) {
    val settings = repository.getVocabularyStore().settings
    var first by remember(settings) { mutableStateOf(settings.shortIntervals[0].toString()) }
    var second by remember(settings) { mutableStateOf(settings.shortIntervals[1].toString()) }
    var long by remember(settings) { mutableStateOf(settings.longIntervalDays.toString()) }
    var adaptive by remember(settings) { mutableStateOf(settings.adaptiveReview) }
    var message by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("记忆与复习", fontWeight = FontWeight.Bold)
            OutlinedTextField(first, { first = it }, label = { Text("认知1：间隔天数（1–7）") })
            OutlinedTextField(second, { second = it }, label = { Text("认知2：间隔天数（1–30）") })
            OutlinedTextField(long, { long = it }, label = { Text("认知3：长期天数（30–365）") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(adaptive, { adaptive = it })
                Text("根据遗忘记录缩短复习间隔")
            }
            Button(onClick = {
                try {
                    repository.setReviewSettings(first.toInt(), second.toInt(), long.toInt(), adaptive)
                    message = "已保存"
                } catch (_: Exception) { message = "请输入有效间隔，第二个间隔不得小于第一个" }
            }) { Text("保存复习设置") }
            Text(message)
        }
    }
}
