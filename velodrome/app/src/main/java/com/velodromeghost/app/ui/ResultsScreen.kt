package com.velodromeghost.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velodromeghost.app.Palette
import com.velodromeghost.app.RaceViewModel
import com.velodromeghost.core.race.Competitor
import com.velodromeghost.core.race.RaceMode
import com.velodromeghost.core.race.RacePhase

@Composable
fun ResultsScreen(vm: RaceViewModel) {
    val raceState by vm.race.collectAsStateWithLifecycle()
    val st = raceState

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (st == null) {
                Text("No race yet")
            } else {
                val finished = st.phase == RacePhase.FINISHED
                val youWon = st.winner == Competitor.RIDER
                Text(
                    when {
                        !finished -> "Race abandoned"
                        youWon -> "YOU WIN!"
                        else -> "THE GHOST WINS"
                    },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = if (youWon) Palette.good else Palette.ghost,
                )
                st.marginSeconds?.let {
                    Text("by %.2f s".format(it), fontSize = 22.sp, color = Color.White)
                }
                HorizontalDivider()
                Line("Distance", "%.0f m (%d laps)".format(st.raceDistanceMeters, st.totalLaps))
                st.rider.finishTimeSec?.let { Line("Your time", formatTime(it)) }
                Line("Ghost time", st.ghost.finishTimeSec?.let(::formatTime) ?: "still racing")
                st.rider.finishTimeSec?.let { t ->
                    Line("Your average speed", "%.1f km/h".format(st.raceDistanceMeters / t * 3.6))
                }

                if (st.mode == RaceMode.OMNIUM_POINTS) {
                    HorizontalDivider()
                    Text("Points race", fontWeight = FontWeight.Bold, color = Palette.accent, fontSize = 20.sp)
                    st.sprints.forEach { sprint ->
                        Line(
                            (if (sprint.isFinal) "Final sprint (x2)" else "Sprint ${sprint.number}") + " · lap ${sprint.lap}",
                            (if (sprint.winner == Competitor.RIDER) "You" else "Ghost") +
                                "   (you ${sprint.riderPoints} / ghost ${sprint.ghostPoints})",
                        )
                    }
                    if (st.riderLapPoints > 0) Line("Laps gained", "+${st.riderLapPoints} you")
                    if (st.ghostLapPoints > 0) Line("Laps gained", "+${st.ghostLapPoints} ghost")
                    Line("TOTAL", "You ${st.riderTotalPoints}  ·  Ghost ${st.ghostTotalPoints}", bold = true)
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.startRace() }) { Text("Race again") }
                OutlinedButton(onClick = { vm.backToSetup() }) { Text("Change setup") }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Palette.muted, modifier = Modifier.weight(1f))
        Text(value, color = Color.White, fontWeight = if (bold) FontWeight.Black else FontWeight.SemiBold)
    }
}

private fun formatTime(sec: Double): String {
    val m = (sec / 60).toInt()
    val s = sec - m * 60
    return if (m > 0) "%d:%06.3f".format(m, s) else "%.3f s".format(s)
}
