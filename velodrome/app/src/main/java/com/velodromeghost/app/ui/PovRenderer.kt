package com.velodromeghost.app.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.velodromeghost.app.Palette
import com.velodromeghost.core.race.RaceState
import com.velodromeghost.core.race.RiderSnapshot
import com.velodromeghost.core.track.Track
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Rider's-eye view: a classic pseudo-3D "road" renderer. The track is sliced into strips ahead
 * of you, projected with a simple pinhole camera, and bent sideways by the track curvature.
 * The horizon tilts as the banking steepens in the bends.
 */
object PovRenderer {
    private const val CAM_HEIGHT = 1.35
    private const val CAM_LATERAL = 1.2
    private const val GHOST_LATERAL = 2.1

    /** Where each rider in a field sits across the track (m from the inside edge). */
    private val FIELD_LANES = doubleArrayOf(2.1, 3.1, 1.4, 4.0, 2.6, 3.6, 1.8, 4.6, 2.9, 1.1, 3.3)
    private const val FAR = 110.0
    private const val SLICES = 80
    private const val CURVE_GAIN = 0.85
    private const val WALL_HEIGHT = 1.1

    private val sky = listOf(Color(0xFF05070F), Color(0xFF1B2440), Color(0xFF3A4566))
    private val infield = Color(0xFF243B2C)
    private val apron = Color(0xFF5FA8D3)
    private val woodLight = Color(0xFFD9A066)
    private val woodDark = Color(0xFFC88F58)
    private val stands = listOf(Color(0xFF3B2F4A), Color(0xFF2C2338))
    private val boards = listOf(Color(0xFF1565C0), Color(0xFFF5F5F5), Color(0xFFC62828), Color(0xFFF5F5F5))

    fun DrawScope.drawPov(state: RaceState, track: Track) {
        val w = size.width.toDouble()
        val h = size.height.toDouble()
        val me = state.rider.distance
        val horizon = h * 0.40
        val focal = max(w, h * 1.2) * 0.85

        // Sample depths, densest near the camera.
        val zNear = CAM_HEIGHT * focal / (h - horizon) * 0.95
        val zs = DoubleArray(SLICES + 1) { i -> zNear * (FAR / zNear).pow(i.toDouble() / SLICES) }

        // Integrate curvature ahead of the rider -> sideways displacement at each depth.
        val disp = DoubleArray(SLICES + 1)
        run {
            var slope = 0.0
            var lateral = 0.0
            var d = 0.0
            var idx = 0
            val stepLen = 0.5
            while (idx <= SLICES) {
                while (idx <= SLICES && zs[idx] <= d) {
                    disp[idx] = lateral
                    idx++
                }
                slope += track.curvature(me + d) * CURVE_GAIN * stepLen
                lateral += slope * stepLen
                d += stepLen
            }
        }

        fun dispAt(z: Double): Double {
            if (z <= zs[0]) return disp[0] * (z / zs[0]).coerceAtLeast(0.0)
            for (i in 1..SLICES) {
                if (z <= zs[i]) {
                    val t = (z - zs[i - 1]) / (zs[i] - zs[i - 1])
                    return disp[i - 1] + (disp[i] - disp[i - 1]) * t
                }
            }
            return disp[SLICES]
        }

        fun sx(z: Double, lateral: Double, dz: Double) = (w / 2 + (lateral - CAM_LATERAL - dz) * focal / z).toFloat()
        fun sy(z: Double, height: Double = 0.0) = (horizon + (CAM_HEIGHT - height) * focal / z).toFloat()

        fun quad(z1: Double, z2: Double, l1: Double, l2: Double, color: Color, d1: Double, d2: Double) {
            val p = Path().apply {
                moveTo(sx(z1, l1, d1), sy(z1))
                lineTo(sx(z1, l2, d1), sy(z1))
                lineTo(sx(z2, l2, d2), sy(z2))
                lineTo(sx(z2, l1, d2), sy(z2))
                close()
            }
            drawPath(p, color)
        }

        fun wall(z1: Double, z2: Double, lat: Double, hBottom: Double, hTop: Double, latTop: Double, color: Color, d1: Double, d2: Double) {
            val p = Path().apply {
                moveTo(sx(z1, lat, d1), sy(z1, hBottom))
                lineTo(sx(z1, latTop, d1), sy(z1, hTop))
                lineTo(sx(z2, latTop, d2), sy(z2, hTop))
                lineTo(sx(z2, lat, d2), sy(z2, hBottom))
                close()
            }
            drawPath(p, color)
        }

        val roll = ((track.bankAngleDeg(me) - Track.STRAIGHT_BANK_DEG) * 0.45).toFloat()
        rotate(degrees = roll, pivot = Offset((w / 2).toFloat(), (h * 0.65).toFloat())) {
            // Background: roof and infield, oversized so rotation never shows the corners.
            drawRect(
                Brush.verticalGradient(sky, startY = (-h).toFloat(), endY = horizon.toFloat()),
                topLeft = Offset((-w).toFloat(), (-h).toFloat()),
                size = Size((3 * w).toFloat(), (horizon + h).toFloat()),
            )
            drawRect(
                infield,
                topLeft = Offset((-w).toFloat(), horizon.toFloat()),
                size = Size((3 * w).toFloat(), (2 * h).toFloat()),
            )
            // Roof lights
            for (i in 0 until 7) {
                val x = (w * (i + 0.5) / 7).toFloat()
                drawCircle(Color(0x55FFF3C4), radius = (w * 0.012).toFloat(), center = Offset(x, (horizon * 0.35).toFloat()))
            }

            for (i in SLICES downTo 1) {
                val zf = zs[i]
                val zn = zs[i - 1]
                val df = disp[i]
                val dn = disp[i - 1]
                val stripe = floor((me + (zf + zn) / 2) / 3.0).toInt() and 1
                // Stands rising behind the outer barrier
                wall(zf, zn, track.width, WALL_HEIGHT, 7.0, track.width + 9.0, stands[stripe], df, dn)
                // Apron (côte d'azur) and the boards
                quad(zf, zn, -1.4, 0.0, apron, df, dn)
                quad(zf, zn, 0.0, track.width, if (stripe == 0) woodLight else woodDark, df, dn)
                // Painted lines
                quad(zf, zn, Track.BLACK_LINE - 0.03, Track.BLACK_LINE + 0.03, Color.Black, df, dn)
                quad(zf, zn, Track.RED_LINE - 0.03, Track.RED_LINE + 0.03, Color(0xFFD32F2F), df, dn)
                quad(zf, zn, Track.BLUE_LINE - 0.03, Track.BLUE_LINE + 0.03, Color(0xFF1E64C8), df, dn)
                // Advertising boards on the barrier
                val board = floor((me + (zf + zn) / 2) / 6.0).toInt().mod(boards.size)
                wall(zf, zn, track.width, 0.0, WALL_HEIGHT, track.width, boards[board], df, dn)
            }

            // Transverse lines: finish (black/white) and the 200 m line.
            fun transverse(lineDistance: Double, color: Color, halfDepth: Double) {
                val z = Track.mod(lineDistance - me, track.lapLength)
                if (z < zNear || z > FAR) return
                quad(z + halfDepth, z - halfDepth, 0.0, track.width, color, dispAt(z + halfDepth), dispAt(z - halfDepth))
            }
            transverse(0.0, Color.White, 0.45)
            transverse(0.0, Color.Black, 0.12)
            transverse(track.twoHundredLineDistance, Color.White, 0.08)

            // The ghosts, furthest first so nearer riders are drawn on top.
            val riders = state.ghosts.filter { !it.eliminated }
                .map { it to track.wrappedDelta(me, it.rider.distance) }
                .filter { (_, z) -> z > zNear * 0.6 && z < FAR }
                .sortedByDescending { (_, z) -> z }
            for ((g, zg) in riders) {
                val lateral = if (state.isField) FIELD_LANES[(g.id - 1) % FIELD_LANES.size] else GHOST_LATERAL
                val x = sx(zg, lateral, dispAt(zg))
                val y = sy(zg)
                val scale = (focal / zg).toFloat()
                drawCyclist(x, y, scale, g.rider, Palette.competitor(g.id, state.fieldSize))
            }
        }

        drawCockpit(w.toFloat(), h.toFloat(), state.rider)
    }

    private fun DrawScope.drawCyclist(x: Float, y: Float, s: Float, rider: RiderSnapshot, color: Color) {
        val body = color.copy(alpha = 0.8f)
        val dark = color.copy(alpha = 0.6f)
        val glow = color.copy(alpha = 0.13f)
        drawCircle(glow, radius = 1.1f * s, center = Offset(x, y - 0.9f * s))

        // Rear wheel seen from behind: a thin upright bar.
        drawRoundRect(
            dark,
            topLeft = Offset(x - max(1f, 0.035f * s), y - 0.68f * s),
            size = Size(max(2f, 0.07f * s), 0.68f * s),
            cornerRadius = CornerRadius(0.03f * s),
        )
        // Pedalling legs
        val phase = rider.crankAngleRad
        val leftFoot = (sin(phase) * 0.17).toFloat() * s
        val rightFoot = -leftFoot
        val legWidth = max(2f, 0.11f * s)
        drawLine(body, Offset(x - 0.1f * s, y - 0.98f * s), Offset(x - 0.11f * s, y - 0.34f * s + leftFoot), legWidth, StrokeCap.Round)
        drawLine(body, Offset(x + 0.1f * s, y - 0.98f * s), Offset(x + 0.11f * s, y - 0.34f * s + rightFoot), legWidth, StrokeCap.Round)
        // Back (crouched, wide from behind) and helmet
        drawOval(body, topLeft = Offset(x - 0.22f * s, y - 1.42f * s), size = Size(0.44f * s, 0.55f * s))
        drawCircle(Color(0xEEFFFFFF), radius = 0.13f * s, center = Offset(x, y - 1.5f * s))
    }

    /** Your front wheel and handlebars, fixed to the screen. */
    private fun DrawScope.drawCockpit(w: Float, h: Float, me: RiderSnapshot) {
        val bar = Color(0xFF15171C)
        val unit = min(w, h)
        drawRoundRect(
            bar,
            topLeft = Offset(w / 2 - unit * 0.012f, h * 0.80f),
            size = Size(unit * 0.024f, h * 0.22f),
            cornerRadius = CornerRadius(unit * 0.01f),
        )
        val path = Path().apply {
            moveTo(w / 2 - unit * 0.30f, h * 1.02f)
            cubicTo(w / 2 - unit * 0.31f, h * 0.90f, w / 2 - unit * 0.26f, h * 0.86f, w / 2 - unit * 0.17f, h * 0.86f)
            lineTo(w / 2 + unit * 0.17f, h * 0.86f)
            cubicTo(w / 2 + unit * 0.26f, h * 0.86f, w / 2 + unit * 0.31f, h * 0.90f, w / 2 + unit * 0.30f, h * 1.02f)
        }
        drawPath(path, bar, style = Stroke(width = unit * 0.035f, cap = StrokeCap.Round))
        // Tiny wheel blur so you feel the speed.
        val blur = (me.speedMps / 18.0).coerceIn(0.0, 1.0).toFloat()
        drawLine(
            Color.White.copy(alpha = 0.15f * blur),
            Offset(w / 2, h * 0.82f),
            Offset(w / 2, h),
            strokeWidth = unit * 0.006f,
        )
    }
}
