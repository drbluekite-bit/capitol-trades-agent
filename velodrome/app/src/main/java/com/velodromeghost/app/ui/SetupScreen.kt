package com.velodromeghost.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.velodromeghost.app.Palette
import com.velodromeghost.app.RaceViewModel
import com.velodromeghost.app.ble.ConnectionState
import com.velodromeghost.app.data.AppSettings
import com.velodromeghost.app.data.ResistanceMode
import com.velodromeghost.core.race.RaceMode
import kotlin.math.roundToInt

private fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun SetupScreen(vm: RaceViewModel) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.consumeMessage()
        }
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Velodrome Ghost", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Palette.rider)
            Text(
                "Race a pace-setting ghost rider round a 250 m track.",
                color = Palette.muted,
            )
            ConnectionCard(vm, s)
            RaceTypeCard(vm, s)
            GhostCard(vm, s)
            RiderCard(vm, s)
            ResistanceCard(vm, s)
            Section("6 · After the race") {
                SwitchRow(
                    "Send ride to ChatGPT automatically",
                    "When a race finishes, open the ChatGPT app with your ride summary and per-second data attached - just press send",
                    s.autoSendToChatGpt,
                ) { on -> vm.update { it.copy(autoSendToChatGpt = on) } }
            }
            Button(
                onClick = { vm.startRace() },
                modifier = Modifier.fillMaxWidth().height(64.dp),
            ) {
                Text("START RACE", fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionCard(vm: RaceViewModel, s: AppSettings) {
    val context = LocalContext.current
    val state by vm.ble.state.collectAsStateWithLifecycle()
    val devices by vm.ble.devices.collectAsStateWithLifecycle()
    val bike by vm.ble.bike.collectAsStateWithLifecycle()

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) vm.ble.startScan()
        else Toast.makeText(context, "Bluetooth permission is needed to find SmartSpin2k", Toast.LENGTH_LONG).show()
    }

    fun withPermissions(action: () -> Unit) {
        val missing = bluetoothPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        when {
            !vm.ble.hasBluetooth -> Toast.makeText(context, "This device has no Bluetooth", Toast.LENGTH_LONG).show()
            missing.isNotEmpty() -> permissionLauncher.launch(missing.toTypedArray())
            !vm.ble.isEnabled -> try {
                enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: SecurityException) {
                Toast.makeText(context, "Please turn Bluetooth on", Toast.LENGTH_LONG).show()
            }
            else -> action()
        }
    }

    Section("1 · Bike (SmartSpin2k + Peloton)") {
        val status = when (val st = state) {
            ConnectionState.Idle -> "Not connected"
            ConnectionState.Scanning -> "Scanning…"
            is ConnectionState.Connecting -> "Connecting to ${st.name}…"
            is ConnectionState.Connected ->
                "Connected to ${st.name}" + if (st.canControl) " · resistance control ready" else " · read-only"
            is ConnectionState.Failed -> st.message
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state == ConnectionState.Scanning || state is ConnectionState.Connecting) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
            }
            Text(
                status,
                color = when (state) {
                    is ConnectionState.Connected -> Palette.good
                    is ConnectionState.Failed -> Palette.bad
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        val live = bike
        if (state is ConnectionState.Connected && live != null) {
            val sample = live.sample
            Text(
                "Live: ${sample.powerWatts ?: "--"} W · ${sample.cadenceRpm?.roundToInt() ?: "--"} rpm" +
                    (sample.heartRate?.let { " · $it bpm" } ?: ""),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state is ConnectionState.Connected) {
                OutlinedButton(onClick = { vm.ble.disconnect() }) { Text("Disconnect") }
            } else {
                Button(onClick = { withPermissions { vm.ble.startScan() } }) { Text("Scan") }
                val last = s.lastDeviceAddress
                if (last != null) {
                    OutlinedButton(onClick = { withPermissions { vm.ble.connect(last, s.lastDeviceName) } }) {
                        Text("Reconnect ${s.lastDeviceName ?: ""}".trim())
                    }
                }
            }
        }
        if (state == ConnectionState.Scanning || (devices.isNotEmpty() && state !is ConnectionState.Connected)) {
            if (devices.isEmpty()) {
                Text(
                    "Looking for devices… Make sure SmartSpin2k is powered and not connected to another app." +
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) " Location must be switched on." else "",
                    color = Palette.muted,
                    fontSize = 13.sp,
                )
            }
            devices.take(12).forEach { d ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { withPermissions { vm.ble.connect(d.address, d.name) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            d.name,
                            fontWeight = if (d.isSmartSpin) FontWeight.Bold else FontWeight.Normal,
                            color = if (d.isSmartSpin) Palette.rider else MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            buildString {
                                append(d.address)
                                if (d.isSmartSpin) append(" · SmartSpin2k")
                                else if (d.advertisesFitness) append(" · trainer / power meter")
                            },
                            fontSize = 12.sp,
                            color = Palette.muted,
                        )
                    }
                    Text("${d.rssi} dBm", fontSize = 12.sp, color = Palette.muted)
                }
                HorizontalDivider()
            }
        }
        SwitchRow(
            "Demo mode (no bike)",
            "Use an on-screen power slider instead of SmartSpin2k",
            s.demoMode,
        ) { on -> vm.update { it.copy(demoMode = on) } }
    }
}

@Composable
private fun RaceTypeCard(vm: RaceViewModel, s: AppSettings) {
    Section("2 · Race") {
        RadioRow(
            "1 v 1 race",
            "Head to head against the ghost. First across the line wins.",
            s.mode == RaceMode.SPRINT_1V1,
        ) { vm.update { it.copy(mode = RaceMode.SPRINT_1V1) } }
        RadioRow(
            "Omnium points race",
            "Long paced race with a sprint every few laps (5 / 3 pts), double points at the finish, +20 for lapping the ghost.",
            s.mode == RaceMode.OMNIUM_POINTS,
        ) { vm.update { it.copy(mode = RaceMode.OMNIUM_POINTS) } }
        HorizontalDivider()
        if (s.mode == RaceMode.SPRINT_1V1) {
            Stepper("Laps", s.sprintLaps, 1..40, suffix = " (${s.sprintLaps * 250} m)") { v ->
                vm.update { it.copy(sprintLaps = v) }
            }
        } else {
            Stepper("Laps", s.omniumLaps, 4..160, step = 2, suffix = " (${s.omniumLaps * 250 / 1000.0} km)") { v ->
                vm.update { it.copy(omniumLaps = v) }
            }
            Stepper("Sprint every", s.sprintEveryLaps, 2..20, suffix = " laps") { v ->
                vm.update { it.copy(sprintEveryLaps = v) }
            }
        }
    }
}

@Composable
private fun GhostCard(vm: RaceViewModel, s: AppSettings) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importWorkout(uri)
    }

    Section("3 · Ghost rider") {
        Text("Pacing", fontWeight = FontWeight.Bold)
        RadioRow(
            "Constant pace",
            "Ghost holds ${s.constantPacePct}% of its FTP",
            s.workoutId == null,
        ) { vm.update { it.copy(workoutId = null) } }
        if (s.workoutId == null) {
            LabeledSlider("Pace", s.constantPacePct, 50..120, "%") { v -> vm.update { it.copy(constantPacePct = v) } }
        }
        workouts.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    RadioRow(
                        w.name,
                        "${formatDuration(w.durationSec)} · ${if (w.builtIn) "built-in" else "imported .zwo"}",
                        s.workoutId == w.id,
                    ) { vm.update { it.copy(workoutId = w.id) } }
                }
                if (!w.builtIn) {
                    TextButton(onClick = { vm.deleteWorkout(w.id) }) { Text("Delete", color = Palette.bad) }
                }
            }
        }
        OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .zwo file…") }
        Text(
            "The ghost rides the workout's power (as % of its FTP); target cadence is shown on screen. " +
                "In ERG mode SmartSpin2k follows the same workout scaled to your FTP.",
            fontSize = 12.sp,
            color = Palette.muted,
        )
        HorizontalDivider()
        val ghostFtp = (s.ftpWatts * s.ghostStrengthPct / 100.0).roundToInt()
        LabeledSlider("Ghost strength", s.ghostStrengthPct, 50..150, "%  (ghost FTP $ghostFtp W)") { v ->
            vm.update { it.copy(ghostStrengthPct = v) }
        }
        SwitchRow(
            "Ghost attacks the sprints",
            "Ghost kicks for the line in the last 200 m before every sprint / the finish",
            s.ghostAttacks,
        ) { on -> vm.update { it.copy(ghostAttacks = on) } }
        if (s.ghostAttacks) {
            LabeledSlider("Ghost sprint power", s.ghostSprintPct, 110..300, "% FTP  (${ghostFtp * s.ghostSprintPct / 100} W)") { v ->
                vm.update { it.copy(ghostSprintPct = v) }
            }
        }
        Stepper("Ghost head start", s.ghostHeadStartM, -100..100, step = 5, suffix = " m") { v ->
            vm.update { it.copy(ghostHeadStartM = v) }
        }
    }
}

@Composable
private fun RiderCard(vm: RaceViewModel, s: AppSettings) {
    Section("4 · You") {
        Stepper("FTP", s.ftpWatts, 80..500, step = 5, suffix = " W") { v -> vm.update { it.copy(ftpWatts = v) } }
        Stepper("Rider weight", s.riderWeightKg, 35..160, suffix = " kg") { v -> vm.update { it.copy(riderWeightKg = v) } }
        Stepper("Bike weight", s.bikeWeightKg, 5..20, suffix = " kg") { v -> vm.update { it.copy(bikeWeightKg = v) } }
    }
}

@Composable
private fun ResistanceCard(vm: RaceViewModel, s: AppSettings) {
    Section("5 · SmartSpin2k resistance") {
        ResistanceMode.entries.forEach { mode ->
            RadioRow(mode.label, mode.help, s.resistance == mode) { vm.update { it.copy(resistance = mode) } }
        }
    }
}

// ---------------------------------------------------------------------- widgets

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.accent)
            content()
        }
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(title, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.muted)
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Palette.muted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Big +/- buttons are easier than a keyboard on a bike-mounted touchscreen. */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    suffix: String = "",
    onChange: (Int) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) {
            Text("−", fontSize = 20.sp)
        }
        Text(
            "$value$suffix",
            Modifier.widthIn(min = 110.dp).padding(horizontal = 8.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
        )
        OutlinedButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) {
            Text("+", fontSize = 20.sp)
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, suffix: String, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value$suffix")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}

fun formatDuration(sec: Double): String {
    val total = sec.roundToInt().coerceAtLeast(0)
    return if (total >= 3600) "%d:%02d:%02d".format(total / 3600, total / 60 % 60, total % 60)
    else "%d:%02d".format(total / 60, total % 60)
}
