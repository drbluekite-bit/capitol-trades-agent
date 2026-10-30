package com.velodromeghost.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.velodromeghost.app.Palette
import com.velodromeghost.core.race.BleepResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Level reached in each bleep test (last 12), oldest on the left; the newest is highlighted. */
@Composable
fun BleepChart(history: List<BleepResult>, modifier: Modifier = Modifier) {
    val results = history.takeLast(12)
    if (results.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val dateFormat = SimpleDateFormat("d MMM", Locale.UK)
    Canvas(modifier.fillMaxWidth().height(150.dp)) {
        val top = 22f
        val bottom = size.height - 22f
        val left = 12f
        val right = size.width - 12f
        val maxLevel = (results.maxOf { it.level } + 1).coerceAtLeast(2)
        val minLevel = (results.minOf { it.level } - 1).coerceAtLeast(0)
        fun x(i: Int) = if (results.size == 1) (left + right) / 2 else left + (right - left) * i / (results.size - 1)
        fun y(level: Int) = bottom - (bottom - top) * (level - minLevel) / (maxLevel - minLevel).toFloat()

        for (level in minLevel..maxLevel) {
            drawLine(Color.White.copy(alpha = 0.08f), Offset(left, y(level)), Offset(right, y(level)), 1f)
        }
        if (results.size > 1) {
            val path = Path()
            results.forEachIndexed { i, r -> if (i == 0) path.moveTo(x(i), y(r.level)) else path.lineTo(x(i), y(r.level)) }
            drawPath(path, Palette.accent.copy(alpha = 0.7f), style = Stroke(width = 3f))
        }
        val small = TextStyle(color = Palette.muted, fontSize = 10.sp)
        results.forEachIndexed { i, r ->
            val latest = i == results.lastIndex
            val p = Offset(x(i), y(r.level))
            drawCircle(if (latest) Palette.rider else Palette.accent, radius = if (latest) 8f else 5f, center = p)
            drawText(
                measurer, "${r.level}", topLeft = Offset(p.x - 8f, p.y - 22f),
                style = TextStyle(color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            if (latest || i == 0 || results.size <= 6) {
                drawText(measurer, dateFormat.format(Date(r.timestampMs)), topLeft = Offset((p.x - 18f).coerceIn(0f, size.width - 40f), bottom + 4f), style = small)
            }
        }
    }
}
