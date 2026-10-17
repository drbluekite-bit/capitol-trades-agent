package com.velodromeghost.app

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.velodromeghost.app.ble.BleManager
import com.velodromeghost.app.ble.ConnectionState
import com.velodromeghost.app.data.AppSettings
import com.velodromeghost.app.data.ResistanceMode
import com.velodromeghost.app.data.SettingsStore
import com.velodromeghost.app.data.WorkoutEntry
import com.velodromeghost.app.data.WorkoutRepository
import com.velodromeghost.core.fit.FitWriter
import com.velodromeghost.core.fit.RideCsv
import com.velodromeghost.core.fit.RideRecorder
import com.velodromeghost.core.fit.RideSummary
import com.velodromeghost.core.physics.Physics
import com.velodromeghost.core.physics.RiderParams
import com.velodromeghost.core.race.Competitor
import com.velodromeghost.core.race.RaceConfig
import com.velodromeghost.core.race.RaceMode
import com.velodromeghost.core.race.RaceEngine
import com.velodromeghost.core.race.RacePhase
import com.velodromeghost.core.race.RaceState
import com.velodromeghost.core.track.Track
import com.velodromeghost.core.workout.Workout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

enum class Screen { SETUP, RACE, RESULTS }

class RaceViewModel(app: Application) : AndroidViewModel(app) {
    val ble = BleManager(app)
    private val store = SettingsStore(app)
    private val repo = WorkoutRepository(app)

    private val _settings = MutableStateFlow(store.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _workouts = MutableStateFlow<List<WorkoutEntry>>(emptyList())
    val workouts: StateFlow<List<WorkoutEntry>> = _workouts.asStateFlow()

    private val _screen = MutableStateFlow(Screen.SETUP)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _race = MutableStateFlow<RaceState?>(null)
    val race: StateFlow<RaceState?> = _race.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    /** Power used in demo mode (no bike connected). */
    private val _demoPower = MutableStateFlow(180f)
    val demoPower: StateFlow<Float> = _demoPower.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    var track: Track = Track()
        private set

    /** Your ride (power, cadence, HR, speed, distance) for the current/last race. */
    private var recorder = RideRecorder()
    private var raceStartUnixMs = 0L

    private val _ride = MutableStateFlow<RideSummary?>(null)
    val ride: StateFlow<RideSummary?> = _ride.asStateFlow()

    /** Set when a race finishes and the ride should be sent to ChatGPT automatically. */
    private val _autoSendPending = MutableStateFlow(false)
    val autoSendPending: StateFlow<Boolean> = _autoSendPending.asStateFlow()

    private var engine: RaceEngine? = null
    private var loop: Job? = null
    private var activeWorkout: Workout? = null

    init {
        refreshWorkouts()
        viewModelScope.launch {
            ble.state.collect { st ->
                if (st is ConnectionState.Connected) {
                    update { it.copy(lastDeviceAddress = st.address, lastDeviceName = st.name, demoMode = false) }
                }
            }
        }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        _settings.update(transform)
        store.save(_settings.value)
    }

    fun setDemoPower(watts: Float) {
        _demoPower.value = watts
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun refreshWorkouts() {
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.list() }
            _workouts.value = list
            if (_settings.value.workoutId != null && list.none { it.id == _settings.value.workoutId }) {
                update { it.copy(workoutId = null) }
            }
        }
    }

    fun importWorkout(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { repo.import(uri) }
            result.onSuccess { entry ->
                _message.value = "Imported \"${entry.name}\""
                update { it.copy(workoutId = entry.id) }
                refreshWorkouts()
            }.onFailure { e ->
                _message.value = "Couldn't import: ${e.message}"
            }
        }
    }

    fun deleteWorkout(id: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(id) }
            if (_settings.value.workoutId == id) update { it.copy(workoutId = null) }
            refreshWorkouts()
        }
    }

    // ------------------------------------------------------------------ race

    fun startRace() {
        val s = _settings.value
        viewModelScope.launch {
            val workout = s.workoutId?.let { id ->
                try {
                    withContext(Dispatchers.IO) { repo.load(id) }
                } catch (e: Exception) {
                    _message.value = "Couldn't load workout: ${e.message}"
                    return@launch
                }
            }
            activeWorkout = workout
            val mass = (s.riderWeightKg + s.bikeWeightKg).toDouble()
            val config = RaceConfig(
                mode = s.mode,
                laps = s.laps,
                sprintEveryLaps = s.sprintEveryLaps,
                riderFtpWatts = s.ftpWatts.toDouble(),
                riderMassKg = mass,
                ghostStrength = s.ghostStrengthPct / 100.0,
                ghostMassKg = mass,
                workout = workout,
                constantGhostFraction = s.constantPacePct / 100.0,
                ghostAttacksSprints = s.ghostAttacks,
                ghostSprintFraction = s.ghostSprintPct / 100.0,
                ghostHeadStartMeters = s.ghostHeadStartM.toDouble(),
            )
            track = config.track
            val newEngine = RaceEngine(config)
            engine = newEngine
            _paused.value = false
            recorder = RideRecorder()
            _ride.value = null
            _autoSendPending.value = false
            raceStartUnixMs = 0L
            _race.value = newEngine.tick(0.0, 0.0, null)
            _screen.value = Screen.RACE
            runLoop(newEngine, s)
        }
    }

    private fun runLoop(engine: RaceEngine, s: AppSettings) {
        loop?.cancel()
        loop = viewModelScope.launch {
            var last = System.nanoTime()
            var lastControl = 0L
            var lastSimCw = -1
            var lastSimSent = 0L
            var finishedAt = 0L
            while (isActive) {
                delay(FRAME_MS)
                val now = System.nanoTime()
                val dt = (now - last) / 1e9
                last = now
                if (_paused.value) continue

                val (power, cadence) = riderInput(s)
                val state = engine.tick(dt, power, cadence)
                _race.value = state
                record(state)

                if (now - lastControl > 1_000_000_000L && state.phase != RacePhase.FINISHED) {
                    lastControl = now
                    when (s.resistance) {
                        ResistanceMode.ERG -> ble.setTargetPower(state.riderTargetWatts.roundToInt())
                        ResistanceMode.SIM -> {
                            val cw = simCw(state.rider.drafting)
                            if (cw != lastSimCw || now - lastSimSent > 5_000_000_000L) {
                                ble.setSimulation(0.0, SIM_CRR, cw / 100.0)
                                lastSimCw = cw
                                lastSimSent = now
                            }
                        }
                        ResistanceMode.OFF -> Unit
                    }
                }

                if (state.phase == RacePhase.FINISHED) {
                    if (finishedAt == 0L) {
                        finishedAt = now
                        releaseResistance(s)
                        if (s.autoSendToChatGpt && !s.demoMode && !recorder.isEmpty) _autoSendPending.value = true
                    } else if (now - finishedAt > 3_500_000_000L) {
                        _screen.value = Screen.RESULTS
                        break
                    }
                }
            }
        }
    }

    private fun record(state: RaceState) {
        if (state.phase == RacePhase.COUNTDOWN) return
        if (raceStartUnixMs == 0L) raceStartUnixMs = System.currentTimeMillis()
        // After the finish race time stops, so further updates add nothing.
        val hr = ble.bike.value?.takeIf { it.isFresh() }?.sample?.heartRate
        recorder.update(
            elapsedSec = state.elapsedSec,
            powerWatts = state.rider.powerWatts,
            cadenceRpm = state.rider.cadenceRpm,
            heartRate = hr,
            speedMps = state.rider.speedMps,
            distanceM = state.rider.distance.coerceAtMost(state.raceDistanceMeters),
        )
        if (state.phase == RacePhase.FINISHED) _ride.value = recorder.summary()
    }

    val hasRide: Boolean get() = !recorder.isEmpty

    fun fitFileName(): String {
        val start = if (raceStartUnixMs > 0) raceStartUnixMs else System.currentTimeMillis()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date(start))
        return "velodrome-ghost_$stamp.fit"
    }

    private fun fitBytes(): ByteArray {
        val start = if (raceStartUnixMs > 0) raceStartUnixMs else System.currentTimeMillis()
        val offset = TimeZone.getDefault().getOffset(start) / 1000
        return FitWriter.encode(start / 1000, recorder.samples, recorder.summary(), offset)
    }

    /** Saves the .fit to a location the user picked (Downloads, Drive, ...). */
    fun exportFit(uri: Uri) {
        if (recorder.isEmpty) return
        val bytes = fitBytes()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: error("couldn't open file")
                }
            }
            _message.value = ok.fold({ "Saved .fit file" }, { "Couldn't save: ${it.message}" })
        }
    }

    fun consumeAutoSend() {
        _autoSendPending.value = false
    }

    /** What gets sent to ChatGPT: a readable summary plus the per-second data as a CSV file. */
    class ChatGptPayload(val text: String, val csv: File, val csvText: String)

    suspend fun chatGptPayload(): ChatGptPayload? {
        if (recorder.isEmpty) return null
        val r = recorder.summary()
        val st = _race.value
        val s = _settings.value
        val start = if (raceStartUnixMs > 0) raceStartUnixMs else System.currentTimeMillis()
        val text = buildString {
            appendLine("Here's my indoor track cycling ride from Velodrome Ghost (Peloton + SmartSpin2k, racing a ghost rider on a virtual 250 m velodrome).")
            appendLine()
            appendLine("Date: " + SimpleDateFormat("EEE d MMM yyyy, HH:mm", Locale.UK).format(Date(start)))
            if (st != null) {
                val type = if (st.mode == RaceMode.SPRINT_1V1) "1v1 race" else "Omnium points race (sprint every ${s.sprintEveryLaps} laps)"
                appendLine("Race: $type, ${st.totalLaps} laps (${st.raceDistanceMeters.roundToInt()} m)")
                val result = when {
                    st.phase != RacePhase.FINISHED -> "abandoned"
                    st.winner == Competitor.RIDER -> "I won" + (st.marginSeconds?.let { " by %.2f s".format(Locale.UK, it) } ?: "")
                    else -> "the ghost won" + (st.marginSeconds?.let { " by %.2f s".format(Locale.UK, it) } ?: "")
                }
                appendLine("Result: $result")
                if (st.mode == RaceMode.OMNIUM_POINTS) {
                    appendLine("Points: me ${st.riderTotalPoints}, ghost ${st.ghostTotalPoints} (sprints won ${st.sprints.count { it.winner == Competitor.RIDER }}/${st.sprints.size})")
                }
            }
            appendLine("Ghost: ${s.ghostStrengthPct}% of my FTP, pacing: ${activeWorkout?.name ?: "constant ${s.constantPacePct}% FTP"}")
            appendLine()
            appendLine("Ride time: ${formatClock(r.durationSec)}")
            appendLine("Distance: %.2f km".format(Locale.UK, r.distanceM / 1000))
            appendLine("Avg power: ${r.avgPowerWatts.roundToInt()} W (max ${r.maxPowerWatts} W)")
            appendLine("Avg cadence: ${r.avgCadenceRpm?.roundToInt()?.let { "$it rpm" } ?: "n/a"}" + (r.maxCadenceRpm?.let { " (max $it rpm)" } ?: ""))
            appendLine("Total work: %.1f kJ".format(Locale.UK, r.totalWorkKj))
            appendLine("Calories: ${r.kcal.roundToInt()} kcal")
            r.avgHeartRate?.let { appendLine("Avg heart rate: $it bpm (max ${r.maxHeartRate} bpm)") }
            appendLine("Avg speed: %.1f km/h".format(Locale.UK, r.avgSpeedMps * 3.6))
            appendLine("My FTP: ${s.ftpWatts} W, weight ${s.riderWeightKg} kg")
            appendLine()
            append("Per-second data is in the attached CSV. Please analyse my ride and pacing, and tell me how to beat the ghost next time.")
        }
        val csvText = RideCsv.write(recorder.samples)
        val name = fitFileName().removeSuffix(".fit") + ".csv"
        val csv = withContext(Dispatchers.IO) {
            val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
            File(dir, name).apply { writeText(csvText) }
        }
        return ChatGptPayload(text, csv, csvText)
    }

    private fun formatClock(sec: Double): String {
        val t = sec.roundToInt()
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }

    /** Writes the .fit into the app cache for sharing (Strava, Garmin Connect, email...). */
    suspend fun fitFileForSharing(): File? {
        if (recorder.isEmpty) return null
        val bytes = fitBytes()
        val name = fitFileName()
        return withContext(Dispatchers.IO) {
            val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
            dir.listFiles { f -> f.extension == "fit" }?.forEach { it.delete() }
            File(dir, name).apply { writeBytes(bytes) }
        }
    }

    private fun riderInput(s: AppSettings): Pair<Double, Double?> {
        if (s.demoMode) return _demoPower.value.toDouble() to null
        val live = ble.bike.value?.takeIf { it.isFresh() } ?: return 0.0 to null
        return (live.sample.powerWatts ?: 0).coerceAtLeast(0).toDouble() to live.sample.cadenceRpm
    }

    /** Wind resistance coefficient (0.5·ρ·CdA) in 0.01 kg/m, reduced while sitting in the draft. */
    private fun simCw(drafting: Boolean): Int {
        val p = RiderParams(1.0)
        val factor = if (drafting) Physics.FULL_DRAFT_FACTOR else 1.0
        return (0.5 * p.airDensity * p.cdA * factor * 100).roundToInt()
    }

    private fun releaseResistance(s: AppSettings) {
        if (s.resistance != ResistanceMode.OFF) ble.setSimulation(0.0, SIM_CRR, simCw(false) / 100.0)
    }

    fun togglePause() {
        _paused.update { !it }
    }

    fun endRace() {
        loop?.cancel()
        loop = null
        releaseResistance(_settings.value)
        val st = _race.value
        val finished = st != null && st.phase == RacePhase.FINISHED
        if (!recorder.isEmpty) _ride.value = recorder.summary()
        // Even an abandoned race can be exported if you rode some of it.
        _screen.value = if (finished || !recorder.isEmpty) Screen.RESULTS else Screen.SETUP
    }

    fun backToSetup() {
        loop?.cancel()
        _screen.value = Screen.SETUP
    }

    val workoutName: String?
        get() = activeWorkout?.name

    override fun onCleared() {
        loop?.cancel()
        ble.disconnect()
    }

    private companion object {
        const val FRAME_MS = 33L
        const val SIM_CRR = 0.0025
    }
}
