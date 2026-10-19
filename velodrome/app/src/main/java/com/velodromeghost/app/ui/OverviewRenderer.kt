package com.velodromeghost.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.velodromeghost.app.Palette
import com.velodromeghost.core.race.RaceState
import com.velodromeghost.core.track.Point
import com.velodromeghost.core.track.Track
import kotlin.math.max
import kotlin.math.min

/** Top-down view of the whole velodrome with you and the ghost on it. */
object OverviewRenderer {
    fun DrawScope.drawOverview(state: RaceState, track: Track, text: TextMeasurer) {
        val margin = 3.0
        val halfW = track.straightLength / 2 + track.bendRadius + track.width + margin
        val halfH = track.bendRadius + track.width + margin
        val scale = (min(size.width / (2 * halfW), size.height / (2 * halfH)) * 0.96).toFloat()
        val cx = size.width / 2
        val cy = size.height / 2

        fun screen(p: Point) = Offset(cx + p.x.toFloat() * scale, cy - p.y.toFloat() * scale)

        fun loop(lateral: Double): Path = Path().apply {
            var u = 0.0
            moveTo(screen(track.pointAtU(0.0, lateral)).x, screen(track.pointAtU(0.0, lateral)).y)
            while (u < track.lapLength) {
                u += 2.0
                val o = screen(track.pointAtU(u, lateral))
                lineTo(o.x, o.y)
            }
            close()
        }

        // Boards, apron, infield (painter's order: outer first).
        drawPath(loop(track.width), Color(0xFFD9A066))
        drawPath(loop(0.0), Color(0xFF5FA8D3))
        drawPath(loop(-1.4), Color(0xFF243B2C))
        val lineWidth = max(1.5f, 0.10f * scale)
        drawPath(loop(Track.BLACK_LINE), Color.Black, style = Stroke(lineWidth))
        drawPath(loop(Track.RED_LINE), Color(0xFFD32F2F), style = Stroke(lineWidth))
        drawPath(loop(Track.BLUE_LINE), Color(0xFF1E64C8), style = Stroke(lineWidth))
        drawPath(loop(track.width), Color(0xFF1B1B1B), style = Stroke(max(2f, 0.25f * scale)))

        fun acrossTrack(distance: Double, color: Color, width: Float) {
            drawLine(
                color,
                screen(track.position(distance, -0.2)),
                screen(track.position(distance, track.width)),
                strokeWidth = width,
            )
        }
        acrossTrack(0.0, Color.White, max(4f, 0.6f * scale))
        acrossTrack(0.0, Color.Black, max(1.5f, 0.2f * scale))
        acrossTrack(track.twoHundredLineDistance, Color.White, max(1.5f, 0.15f * scale))

        val small = TextStyle(color = Color(0xFFB0BEC5), fontSize = 10.sp)
        val f = screen(track.position(0.0, track.width + 1.2))
        drawText(text, "FINISH", topLeft = Offset(f.x - 18f, f.y), style = small)

        val dot = max(7f, 1.3f * scale)
        val label = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
        val toCentre = { p: Offset -> Offset((cx - p.x) * 0.12f, (cy - p.y) * 0.12f) }
        val me = screen(track.position(state.rider.distance, 1.0))

        val field = state.isField
        state.ghosts.filter { !it.eliminated }.forEach { g ->
            val color = Palette.competitor(g.id, state.fieldSize)
            val lateral = if (field) 1.6 + ((g.id * 1.37) % 4.2) else 2.6
            val p = screen(track.position(g.rider.distance, lateral))
            val r = if (field) dot * 0.8f else dot
            drawCircle(color.copy(alpha = 0.25f), radius = r * 2f, center = p)
            drawCircle(color, radius = r, center = p)
            if (!field) {
                drawText(text, "GHOST", topLeft = p + toCentre(p) + Offset(-18f, -dot * 3.2f), style = label.copy(color = color))
            }
        }
        drawCircle(Color.White, radius = dot * 1.25f, center = me)
        drawCircle(Palette.rider, radius = dot, center = me)
        drawText(text, "YOU", topLeft = me + toCentre(me) + Offset(-12f, dot * 1.2f), style = label.copy(color = Palette.rider))

        // A short line linking you to a lone ghost when close, to read the gap at a glance.
        if (!field && kotlin.math.abs(track.wrappedDelta(state.rider.distance, state.ghost.distance)) < 30) {
            val ghost = screen(track.position(state.ghost.distance, 2.6))
            drawLine(Color.White.copy(alpha = 0.4f), me, ghost, strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}
