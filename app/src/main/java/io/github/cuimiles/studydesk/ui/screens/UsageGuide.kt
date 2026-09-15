package io.github.cuimiles.studydesk.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun UsageGuide() {
    var open by remember { mutableStateOf(false) }
    var license by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    TextButton(onClick = { open = true }) { Text("使用说明") }
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("使用说明") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Text("单词旁的三颗竖点表示认知程度，蓝点从下向上点亮。认识加一，不认识减一，最低零；达到三颗后默认90天再复习。间隔可在设置调整，系统会根据遗忘记录缩短间隔。")
                Text("扬声器：首次自动读一次，也可点击重播。朗读需要手机英语离线语音包，缺失时可按提示安装。")
                Text("星星：空心表示未收藏，金色实心表示已收藏。翻译图标：显示或隐藏中文，中文默认隐藏。下箭头：展开更多例句。")
                Text("先看单词与例句，再判断认识或不认识。判断后显示英文释义，点下一词继续；三角标记表示重难点。熟：跳过该词，可在熟词本中重学。")
                Text("两次隔天遗忘会加入重难点库，三次跨日成功后移出。学习记录、认知值和当前词卡保存在本机，可在设置中备份与恢复。")
                Text("章节和搜索词卡的问号可展开提问，复制时包含问题与当前释义。在线AI尚未连接；五步解析属于AI辅助内容，与词典原文分开存储。")
                Text("释义与例句来源：Open English Wordnet 2025（CC BY 4.0）及 Princeton WordNet 3.0。修改包括词表筛选、词形关联和显示排序。词义按历史语料标注频次排列，缺失时回退词典顺序，不代表当代雅思精确词频。原句来源逐条保存在词库中。")
                Text("Open English Wordnet Community / Princeton University。来源：en-word.net/downloads、wordnet.princeton.edu；词表由用户提供并授权，保留原作者署名。")
                TextButton(onClick = { license = "CC-BY-4.0.txt" }) { Text("CC BY 4.0 许可全文") }
                TextButton(onClick = { license = "PRINCETON-WORDNET-LICENSE.txt" }) { Text("Princeton WordNet 许可全文") }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("关闭") } }
    )
    license?.let { name ->
        val text = remember(name) { context.assets.open(name).bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { license = null }, title = { Text("数据许可") },
            text = { Text(text, Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { license = null }) { Text("关闭") } })
    }
}
