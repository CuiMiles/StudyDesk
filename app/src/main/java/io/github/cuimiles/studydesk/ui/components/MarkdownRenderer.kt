package io.github.cuimiles.studydesk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import io.github.cuimiles.studydesk.core.fivestep.FiveStepPayload
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.regex.Pattern

/**
 * 结构化 Markdown 块模型
 */
sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class Callout(val title: String, val body: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
}

/**
 * 将多行原始 Markdown 解析为块列表
 */
fun parseMarkdownBlocks(raw: String): List<MarkdownBlock> {
    val lines = raw.replace("\r\n", "\n").split("\n")
    val blocks = mutableListOf<MarkdownBlock>()
    var i = 0
    val n = lines.size

    val calloutPrefixRegex = Pattern.compile(
        "^\\*\\*(Core feeling|核心感觉|什么时候|When is|Key difference|关键差异|核心对比)[^:*]*[:：]?\\*\\*\\s*(.*)$",
        Pattern.CASE_INSENSITIVE
    )
    val numberedRegex = Pattern.compile("^(\\d+)[.、)]\\s+(.*)$")

    while (i < n) {
        val line = lines[i].trim()
        if (line.isEmpty()) {
            i++
            continue
        }

        // 1. 表格解析 (| Header | Header |)
        if (line.startsWith("|") && line.endsWith("|")) {
            val tableLines = mutableListOf<String>()
            while (i < n && lines[i].trim().startsWith("|") && lines[i].trim().endsWith("|")) {
                tableLines.add(lines[i].trim())
                i++
            }
            if (tableLines.size >= 2) {
                val headers = tableLines[0].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val rows = mutableListOf<List<String>>()
                val startIdx = if (tableLines.size > 1 && tableLines[1].contains("---")) 2 else 1
                for (rIdx in startIdx until tableLines.size) {
                    val cells = tableLines[rIdx].split("|").map { it.trim() }
                    val cleanCells = if (cells.size >= 2) cells.subList(1, cells.size - 1) else cells
                    if (cleanCells.any { it.isNotBlank() }) {
                        rows.add(cleanCells)
                    }
                }
                if (headers.isNotEmpty() && rows.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Table(headers, rows))
                    continue
                }
            }
        }

        // 2. 块引用解析 (> Quote)
        if (line.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < n) {
                val cur = lines[i].trim()
                if (cur.isEmpty()) {
                    i++
                    break
                }
                if (cur.startsWith(">")) {
                    quoteLines.add(cur.removePrefix(">").trim())
                } else if (!cur.startsWith("#") && !cur.startsWith("|") && !cur.startsWith("- ") && !cur.startsWith("* ")) {
                    quoteLines.add(cur)
                } else {
                    break
                }
                i++
            }
            blocks.add(MarkdownBlock.Quote(quoteLines.joinToString(" ")))
            continue
        }

        // 3. 高亮要点 Callout (**核心感觉：** ...)
        val calloutMatcher = calloutPrefixRegex.matcher(line)
        if (calloutMatcher.matches()) {
            val title = calloutMatcher.group(1).trim()
            val body = calloutMatcher.group(2).trim()
            val extraLines = mutableListOf<String>()
            i++
            while (i < n && lines[i].trim().isNotEmpty() &&
                !lines[i].trim().startsWith("#") && !lines[i].trim().startsWith("|") &&
                !lines[i].trim().startsWith(">") && !lines[i].trim().startsWith("- ") &&
                !calloutPrefixRegex.matcher(lines[i].trim()).matches()
            ) {
                extraLines.add(lines[i].trim())
                i++
            }
            val fullBody = if (extraLines.isEmpty()) body else (listOf(body) + extraLines).joinToString(" ").trim()
            blocks.add(MarkdownBlock.Callout(title, fullBody))
            continue
        }

        // 4. 标题 (# Header)
        if (line.startsWith("#")) {
            var level = 0
            while (level < line.length && line[level] == '#') {
                level++
            }
            val text = line.substring(level).trim()
            blocks.add(MarkdownBlock.Header(level, text))
            i++
            continue
        }

        // 5. 无序列表项 (- Item, * Item, • Item)
        if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
            val text = line.substring(2).trim()
            blocks.add(MarkdownBlock.BulletItem(text))
            i++
            continue
        }

        // 6. 有序列表项 (1. Item)
        val numMatcher = numberedRegex.matcher(line)
        if (numMatcher.matches()) {
            val num = numMatcher.group(1)
            val text = numMatcher.group(2).trim()
            blocks.add(MarkdownBlock.NumberedItem(num, text))
            i++
            continue
        }

        // 7. 常规段落
        val paraLines = mutableListOf(line)
        i++
        while (i < n) {
            val next = lines[i].trim()
            if (next.isEmpty() || next.startsWith("#") || next.startsWith("|") ||
                next.startsWith(">") || next.startsWith("- ") || next.startsWith("* ") ||
                numMatcher.reset(next).matches() || calloutPrefixRegex.matcher(next).matches()
            ) {
                break
            }
            paraLines.add(next)
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

/**
 * 行内 Markdown 解析器：
 * 消除原始 **、*、` 符号，转换为加粗、斜体与代码等富文本 SpanStyle
 */
fun parseMarkdownInline(
    text: String,
    baseColor: Color,
    boldColor: Color = baseColor,
    codeBgColor: Color = Color.Black.copy(alpha = 0.06f)
): AnnotatedString {
    return buildAnnotatedString {
        val pattern = Pattern.compile("(`[^`]+`|\\*\\*\\*[^*]+\\*\\*\\*|\\*\\*[^*]+\\*\\*|\\*[^*]+\\*|_[^_]+_)")
        val matcher = pattern.matcher(text)
        var lastEnd = 0

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                append(text.substring(lastEnd, matcher.start()))
            }
            val token = matcher.group()
            when {
                token.startsWith("`") && token.endsWith("`") -> {
                    pushStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBgColor,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    append(token.substring(1, token.length - 1))
                    pop()
                }
                token.startsWith("***") && token.endsWith("***") -> {
                    pushStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            color = boldColor
                        )
                    )
                    append(token.substring(3, token.length - 3))
                    pop()
                }
                token.startsWith("**") && token.endsWith("**") -> {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor))
                    val inner = token.substring(2, token.length - 2)
                    if (inner.contains("*")) {
                        // 内部斜体处理
                        val innerPattern = Pattern.compile("\\*([^*]+)\\*")
                        val innerMatcher = innerPattern.matcher(inner)
                        var innerEnd = 0
                        while (innerMatcher.find()) {
                            if (innerMatcher.start() > innerEnd) {
                                append(inner.substring(innerEnd, innerMatcher.start()))
                            }
                            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                            append(innerMatcher.group(1))
                            pop()
                            innerEnd = innerMatcher.end()
                        }
                        if (innerEnd < inner.length) {
                            append(inner.substring(innerEnd))
                        }
                    } else {
                        append(inner)
                    }
                    pop()
                }
                (token.startsWith("*") && token.endsWith("*")) || (token.startsWith("_") && token.endsWith("_")) -> {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(token.substring(1, token.length - 1))
                    pop()
                }
            }
            lastEnd = matcher.end()
        }

        if (lastEnd < text.length) {
            append(text.substring(lastEnd))
        }
    }
}

/**
 * 原生优雅 Markdown 内容渲染组件
 */
@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    baseFontSize: TextUnit = 13.5.sp,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    if (markdown.isBlank()) return

    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerFontSize = when (block.level) {
                        1 -> 16.sp
                        2 -> 15.sp
                        else -> 14.sp
                    }
                    val headerText = remember(block.text, textColor, accentColor) {
                        parseMarkdownInline(block.text, accentColor, accentColor)
                    }
                    Text(
                        text = headerText,
                        fontSize = headerFontSize,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.Table -> {
                    MarkdownTableView(
                        headers = block.headers,
                        rows = block.rows,
                        baseFontSize = baseFontSize
                    )
                }

                is MarkdownBlock.Quote -> {
                    MarkdownQuoteView(
                        text = block.text,
                        baseFontSize = baseFontSize
                    )
                }

                is MarkdownBlock.Callout -> {
                    MarkdownCalloutView(
                        title = block.title,
                        body = block.body,
                        baseFontSize = baseFontSize
                    )
                }

                is MarkdownBlock.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 8.dp)
                                .size(5.dp)
                                .background(accentColor, CircleShape)
                        )
                        val annotated = remember(block.text, textColor, accentColor) {
                            parseMarkdownInline(block.text, textColor, accentColor)
                        }
                        Text(
                            text = annotated,
                            fontSize = baseFontSize,
                            lineHeight = (baseFontSize.value * 1.5).sp,
                            color = textColor
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 2.dp, end = 8.dp)
                        ) {
                            Text(
                                text = block.number,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        val annotated = remember(block.text, textColor, accentColor) {
                            parseMarkdownInline(block.text, textColor, accentColor)
                        }
                        Text(
                            text = annotated,
                            fontSize = baseFontSize,
                            lineHeight = (baseFontSize.value * 1.5).sp,
                            color = textColor
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text, textColor, accentColor) {
                        parseMarkdownInline(block.text, textColor, accentColor)
                    }
                    Text(
                        text = annotated,
                        fontSize = baseFontSize,
                        lineHeight = (baseFontSize.value * 1.55).sp,
                        color = textColor
                    )
                }
            }
        }
    }
}

/**
 * 表格优雅渲染组件
 */
@Composable
fun MarkdownTableView(
    headers: List<String>,
    rows: List<List<String>>,
    baseFontSize: TextUnit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val isTwoCols = headers.size == 2

            // 表头
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                headers.forEachIndexed { idx, h ->
                    val weight = if (isTwoCols) (if (idx == 0) 0.32f else 0.68f) else (1f / headers.size)
                    val annotated = parseMarkdownInline(
                        text = h,
                        baseColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        boldColor = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = annotated,
                        fontWeight = FontWeight.Bold,
                        fontSize = (baseFontSize.value * 0.95).sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(weight)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // 数据行
            rows.forEachIndexed { rIdx, row ->
                val rowBg = if (rIdx % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                } else {
                    Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEachIndexed { cIdx, cell ->
                        val weight = if (isTwoCols) (if (cIdx == 0) 0.32f else 0.68f) else (1f / headers.size.coerceAtLeast(1))
                        val isFirstCol = cIdx == 0
                        val annotated = parseMarkdownInline(
                            text = cell,
                            baseColor = if (isFirstCol) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            boldColor = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = annotated,
                            fontSize = (baseFontSize.value * 0.92).sp,
                            lineHeight = (baseFontSize.value * 1.35).sp,
                            fontWeight = if (isFirstCol) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(weight)
                        )
                    }
                }
                if (rIdx < rows.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                }
            }
        }
    }
}

/**
 * 篇章故事例句 (Quote) 专属卡片渲染
 */
@Composable
fun MarkdownQuoteView(
    text: String,
    baseFontSize: TextUnit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
        ) {
            // 左侧竖向重点色彩标识条
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(4.dp)
                    .background(MaterialTheme.colorScheme.secondary)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Text(
                        text = "📖 语境篇章例句",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
                val annotated = parseMarkdownInline(
                    text = text,
                    baseColor = MaterialTheme.colorScheme.onSurface,
                    boldColor = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = annotated,
                    fontSize = baseFontSize,
                    lineHeight = (baseFontSize.value * 1.55).sp,
                    fontStyle = FontStyle.Normal
                )
            }
        }
    }
}

/**
 * 核心感觉与语义要点 (Callout) 专属渲染
 */
@Composable
fun MarkdownCalloutView(
    title: String,
    body: String,
    baseFontSize: TextUnit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                Text(
                    text = "🎯",
                    fontSize = 13.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            val annotated = parseMarkdownInline(
                text = body,
                baseColor = MaterialTheme.colorScheme.onTertiaryContainer,
                boldColor = MaterialTheme.colorScheme.tertiary
            )
            Text(
                text = annotated,
                fontSize = baseFontSize,
                lineHeight = (baseFontSize.value * 1.5).sp,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

/**
 * 五步解析步骤卡片组件 (支持中英双版本流畅排版与展开)
 */
@Composable
fun StepItemCard(
    stepNum: String,
    stepTitle: String,
    enContent: String,
    zhContent: String = "",
    globalShowZh: Boolean = false
) {
    var localShowZh by remember(enContent, zhContent) { mutableStateOf<Boolean?>(null) }
    val showZh = localShowZh ?: globalShowZh

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // 步骤标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = stepNum,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = stepTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (zhContent.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (showZh) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.clip(RoundedCornerShape(14.dp))
                    ) {
                        TextButton(
                            onClick = { localShowZh = !showZh },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(
                                text = if (showZh) "隐藏中文" else "中文解析",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (showZh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 英文沉浸区 (Markdown 精准渲染)
            if (enContent.isNotBlank()) {
                MarkdownContent(
                    markdown = enContent,
                    baseFontSize = 13.5.sp,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    accentColor = MaterialTheme.colorScheme.primary
                )
            }

            // 中文解析区 (流畅手风琴折叠展开)
            AnimatedVisibility(
                visible = showZh && zhContent.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 6.dp)
                            ) {
                                Text(
                                    text = "🇨🇳 中文解析",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            MarkdownContent(
                                markdown = zhContent,
                                baseFontSize = 13.sp,
                                textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                accentColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 完整的五步语感深度解析模块组件
 */
@Composable
fun FiveStepSection(
    payload: FiveStepPayload,
    modifier: Modifier = Modifier
) {
    var expanded by remember(payload) { mutableStateOf(false) }
    var globalShowZh by remember(payload) { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "✨ AI 五步语感深度解析",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (expanded) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (globalShowZh) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 4.dp).clip(RoundedCornerShape(12.dp))
                        ) {
                            TextButton(
                                onClick = { globalShowZh = !globalShowZh },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text(
                                    text = if (globalShowZh) "全部英文" else "中英对照",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (globalShowZh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { expanded = !expanded },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (expanded) "收起" else "展开解析",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (payload.concreteImage.isNotBlank() || payload.concreteImageZh.isNotBlank()) {
                        StepItemCard("1", "画面锚定 · Imagery Anchor", payload.concreteImage, payload.concreteImageZh, globalShowZh)
                    }
                    if (payload.synonymsComparison.isNotBlank() || payload.synonymsComparisonZh.isNotBlank()) {
                        StepItemCard("2", "语义场对比 · Semantic Contrast", payload.synonymsComparison, payload.synonymsComparisonZh, globalShowZh)
                    }
                    if (payload.registerAndContexts.isNotBlank() || payload.registerAndContextsZh.isNotBlank()) {
                        StepItemCard("3", "语域感知 · Register Perception", payload.registerAndContexts, payload.registerAndContextsZh, globalShowZh)
                    }
                    if (payload.collocations.isNotBlank() || payload.collocationsZh.isNotBlank()) {
                        StepItemCard("4", "语义韵与搭配 · Collocations", payload.collocations, payload.collocationsZh, globalShowZh)
                    }
                    if (payload.associations.isNotBlank() || payload.associationsZh.isNotBlank()) {
                        StepItemCard("5", "联想网络 · Associations", payload.associations, payload.associationsZh, globalShowZh)
                    }
                    if (payload.integratedExample.isNotBlank() || payload.integratedExampleZh.isNotBlank()) {
                        StepItemCard("6", "综合示例 · Comprehensive Example", payload.integratedExample, payload.integratedExampleZh, globalShowZh)
                    }
                    if (payload.integratedExampleMapping.isNotBlank() || payload.integratedExampleMappingZh.isNotBlank()) {
                        StepItemCard("💡", "综合示例解析 · Example Breakdown", payload.integratedExampleMapping, payload.integratedExampleMappingZh, globalShowZh)
                    }
                    if (payload.chineseExplanation.isNotBlank() && payload.concreteImageZh.isBlank()) {
                        StepItemCard("🇨🇳", "中文解析 · Chinese Explanation", "", payload.chineseExplanation, true)
                    }
                }
            }
        }
    }
}

