package com.velodromeghost.app.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.velodromeghost.core.fit.RideSummary
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
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
    val ride by vm.ride.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    val autoSend by vm.autoSendPending.collectAsStateWithLifecycle()

    fun sendToChatGpt() {
        scope.launch {
            val p = vm.chatGptPayload() ?: return@launch
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", p.csv)
            val withFile = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_TEXT, p.text)
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri(p.csv.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // If ChatGPT won't take a file, paste the data into the message instead.
            val csv = p.csvText.lines().let { rows ->
                if (p.csvText.length <= MAX_INLINE_CSV) p.csvText
                else (listOf(rows.first()) + rows.drop(1).filterIndexed { i, _ -> i % 5 == 0 }).joinToString("\n")
            }
            val textOnly = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, p.text.replace("in the attached CSV", "below") + "\n\n" + csv)
            }
            val pm = context.packageManager
            val target = listOf(withFile, textOnly)
                .map { Intent(it).setPackage(CHATGPT_PACKAGE) }
                .firstOrNull { pm.queryIntentActivities(it, 0).isNotEmpty() }
            try {
                if (target != null) {
                    context.startActivity(target)
                } else {
                    Toast.makeText(context, "ChatGPT app not found - pick an app to send to", Toast.LENGTH_LONG).show()
                    context.startActivity(Intent.createChooser(withFile, "Send ride to"))
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't open ChatGPT: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(autoSend) {
        if (autoSend) {
            vm.consumeAutoSend()
            sendToChatGpt()
        }
    }

    val saveFit = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) vm.exportFit(uri) }

    fun shareFit() {
        scope.launch {
            val file = vm.fitFileForSharing() ?: return@launch
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share .fit file"))
        }
    }

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
            ride?.let { r ->
                HorizontalDivider()
                RideStats(r)
                Button(onClick = { sendToChatGpt() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Send to ChatGPT", fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { saveFit.launch(vm.fitFileName()) }) { Text("Save .fit file") }
                    OutlinedButton(onClick = { shareFit() }) { Text("Share .fit") }
                }
                Text(
                    "Upload the .fit to Strava, Garmin Connect, TrainingPeaks or intervals.icu.",
                    fontSize = 12.sp,
                    color = Palette.muted,
                )
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { vm.startRace() }) { Text("Race again") }
                OutlinedButton(onClick = { vm.backToSetup() }) { Text("Change setup") }
            }
        }
    }
}

private const val CHATGPT_PACKAGE = "com.openai.chatgpt"
private const val MAX_INLINE_CSV = 90_000

@Composable
private fun RideStats(r: RideSummary) {
    Text("Your ride", fontWeight = FontWeight.Bold, color = Palette.accent, fontSize = 20.sp)
    Line("Avg cadence", r.avgCadenceRpm?.let { "${it.roundToInt()} rpm" } ?: "--")
    Line("Avg power", "${r.avgPowerWatts.roundToInt()} W  (max ${r.maxPowerWatts} W)")
    Line("Total work", "%.1f kJ".format(r.totalWorkKj))
    Line("Total distance", "%.2f km".format(r.distanceM / 1000))
    Line("Calories", "${r.kcal.roundToInt()} kcal")
    r.avgHeartRate?.let { Line("Avg heart rate", "$it bpm") }
    Line("Ride time", formatDuration(r.durationSec))
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
