package io.github.cuimiles.studydesk.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import io.github.cuimiles.studydesk.R

@Composable
fun RecognitionDots(value: Int) {
    Column(Modifier.width(6.dp).semantics(mergeDescendants = true) {
        contentDescription = "认知程度"
        stateDescription = "${value.coerceIn(0, 3)}/3"
    }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (i in 2 downTo 0) Box(Modifier.size(6.dp).background(
            if (value > i) Color(0xFF007AFF) else Color(0xFFC2C7D0), CircleShape))
    }
}

@Composable
fun FavoriteIcon(selected: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Icon(painterResource(if (selected) R.drawable.ic_star_filled else R.drawable.ic_star),
            contentDescription = if (selected) "取消收藏" else "收藏",
            tint = if (selected) Color(0xFFE99A00) else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ChineseIcon(selected: Boolean, onClick: () -> Unit) {
    IconToggleButton(checked = selected, onCheckedChange = { onClick() }) {
        Icon(painterResource(R.drawable.ic_translate), contentDescription = if (selected) "隐藏中文" else "显示中文",
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
