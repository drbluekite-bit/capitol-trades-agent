package com.velodromeghost.app.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velodromeghost.app.Palette
import com.velodromeghost.app.RaceViewModel
import com.velodromeghost.app.data.ResistanceMode
import com.velodromeghost.app.ui.OverviewRenderer.drawOverview
import com.velodromeghost.app.ui.PovRenderer.drawPov
import com.velodromeghost.core.race.Competitor
import com.velodromeghost.core.race.RaceMode
import com.velodromeghost.core.race.RacePhase
import com.velodromeghost.core.race.RaceState
import com.velodromeghost.core.track.Track
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun RaceScreen(vm: RaceViewModel) {
    val raceState by vm.race.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val paused by vm.paused.collectAsStateWithLifecycle()
    val demoPower by vm.demoPower.collectAsStateWithLifecycle()
    val st = raceState ?: return

    Immersive()
    BackHandler { vm.endRace() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val landscape = maxWidth > maxHeight
            if (landscape) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1.15f).fillMaxHeight()) { PovPanel(st, vm.track, settings.resistance, vm.workoutName) }
                    Spacer(Modifier.width(2.dp).fillMaxHeight().background(Color(0xFF333A50)))
                    Box(Modifier.weight(1f).fillMaxHeight()) { OverviewPanel(st, vm.track) }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1.1f).fillMaxWidth()) { PovPanel(st, vm.track, settings.resistance, vm.workoutName) }
                    Spacer(Modifier.height(2.dp).fillMaxWidth().background(Color(0xFF333A50)))
                    Box(Modifier.weight(1f).fillMaxWidth()) { OverviewPanel(st, vm.track) }
                }
            }
            if (st.phase == RacePhase.COUNTDOWN) {
                Text(
                    ceil(st.countdownRemaining).toInt().coerceAtLeast(1).toString(),
                    Modifier.align(Alignment.Center),
                    fontSize = 140.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                )
            }
            if (paused) {
                Text(
                    "PAUSED",
                    Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(24.dp),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    color = Palette.accent,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF10141F)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(onClick = { vm.togglePause() }) { Text(if (paused) "Resume" else "Pause") }
            OutlinedButton(onClick = { vm.endRace() }) { Text("End") }
            if (settings.demoMode) {
                Text("Demo power ${demoPower.roundToInt()} W", color = Palette.rider, fontWeight = FontWeight.Bold)
                Slider(
                    value = demoPower,
                    onValueChange = vm::setDemoPower,
                    valueRange = 0f..1200f,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
                Text(
                    when (settings.resistance) {
                        ResistanceMode.ERG -> "ERG ${st.riderTargetWatts.roundToInt()} W"
                        ResistanceMode.SIM -> if (st.rider.drafting) "SIM · drafting" else "SIM · flat track"
                        ResistanceMode.OFF -> "Manual resistance"
                    },
                    color = Palette.muted,
                )
            }
        }
    }
}

@Composable
private fun Immersive() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun PovPanel(st: RaceState, track: Track, resistance: ResistanceMode, workoutName: String?) {
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            clipRect { drawPov(st, track) }
        }
        // Your numbers
        Column(
            Modifier.align(Alignment.TopStart).padding(10.dp).hud(),
        ) {
            Big("${st.rider.powerWatts.roundToInt()}", "W", Palette.rider)
            Big("${st.rider.cadenceRpm.roundToInt()}", "rpm", Color.White, size = 26.sp)
            st.targetCadence?.let { Small("target $it rpm", Palette.accent) }
            if (resistance == ResistanceMode.ERG) Small("ERG ${st.riderTargetWatts.roundToInt()} W", Palette.accent)
            Small("%.1f km/h".format(st.rider.speedKmh), Color.White)
        }
        // Ghost numbers
        Column(
            Modifier.align(Alignment.TopEnd).padding(10.dp).hud(),
            horizontalAlignment = Alignment.End,
        ) {
            Small("GHOST", Palette.ghost)
            Big("${st.ghost.powerWatts.roundToInt()}", "W", Palette.ghost, size = 26.sp)
            Small("${st.ghost.cadenceRpm.roundToInt()} rpm · %.1f km/h".format(st.ghost.speedKmh), Palette.ghost)
            workoutName?.let { Small(it, Palette.muted) }
        }
        // Race status / announcements
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "LAP ${st.currentLap}/${st.totalLaps}",
                Modifier.hud(),
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
            )
            val showAnnouncement = st.announcement != null && st.elapsedSec - st.announcementAtSec < 4.0
            if (showAnnouncement) {
                Text(
                    st.announcement.orEmpty(),
                    Modifier.padding(top = 6.dp).widthIn(max = 320.dp)
                        .background(Color(0xDD000000), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Palette.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        // Where's the ghost if you can't see it?
        val ahead = track.wrappedDelta(st.rider.distance, st.ghost.distance)
        val hint = when {
            ahead > -1.5 && ahead < 1.5 -> "Ghost alongside!"
            ahead < 0 -> "▼ Ghost %.1f m behind".format(-ahead)
            else -> null
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (st.rider.drafting) Small("IN THE DRAFT", Palette.good)
            hint?.let { Small(it, Palette.ghost) }
        }
    }
}

@Composable
private fun OverviewPanel(st: RaceState, track: Track) {
    val measurer = rememberTextMeasurer()
    Column(Modifier.fillMaxSize().background(Color(0xFF0B1020)).padding(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Small("%.0f m to go".format(st.distanceToGo), Color.White)
            Small(formatDuration(st.elapsedSec), Color.White)
        }
        Canvas(Modifier.weight(1f).fillMaxWidth()) { drawOverview(st, track, measurer) }
        GapBar(st)
        if (st.mode == RaceMode.OMNIUM_POINTS) PointsBoard(st)
    }
}

@Composable
private fun GapBar(st: RaceState) {
    val gap = st.gapMeters
    val color = if (gap >= 0) Palette.good else Palette.bad
    val label = when {
        abs(gap) < 0.5 -> "Level with the ghost"
        gap > 0 -> "You +%.1f m  (%.1f s)".format(gap, st.gapSeconds)
        else -> "Ghost +%.1f m  (%.1f s)".format(-gap, -st.gapSeconds)
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val range = 50f
            val mid = size.width / 2
            drawRoundRect(Color(0xFF263049), size = size, cornerRadius = CornerRadius(8f))
            drawLine(Color.White.copy(alpha = 0.5f), Offset(mid, 0f), Offset(mid, size.height), 2f)
            val x = mid + (gap.toFloat() / range).coerceIn(-1f, 1f) * (mid - 6f)
            drawRoundRect(
                color.copy(alpha = 0.5f),
                topLeft = Offset(minOf(mid, x), 3f),
                size = Size(abs(x - mid), size.height - 6f),
                cornerRadius = CornerRadius(4f),
            )
            drawCircle(color, radius = size.height / 2, center = Offset(x, size.height / 2))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Small("ghost ahead", Palette.muted)
            Small("you ahead", Palette.muted)
        }
    }
}

@Composable
private fun PointsBoard(st: RaceState) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).background(Color(0xFF151C33), RoundedCornerShape(8.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Small("POINTS", Palette.accent)
            Text(
                "YOU ${st.riderTotalPoints}   ·   GHOST ${st.ghostTotalPoints}",
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 18.sp,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            val next = st.nextSprintLap
            if (next != null) {
                val lapsToGo = next - st.riderLapsCompleted
                Small(
                    if (next == st.totalLaps) "Final sprint (x2) in $lapsToGo laps" else "Sprint in $lapsToGo laps",
                    Color.White,
                )
            }
            Small("Sprints won ${st.sprints.count { it.winner == Competitor.RIDER }}/${st.sprints.size}", Palette.muted)
        }
    }
}

private fun Modifier.hud() = this.background(Color(0x88000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)

@Composable
private fun Big(value: String, unit: String, color: Color, size: TextUnit = 40.sp) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, color = color, fontSize = size, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
        Text(" $unit", color = color, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
    }
}

@Composable
private fun Small(text: String, color: Color) {
    Text(text, color = color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}
