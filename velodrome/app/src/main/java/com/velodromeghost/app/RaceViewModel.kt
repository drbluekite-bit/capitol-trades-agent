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
import com.velodromeghost.core.physics.Physics
import com.velodromeghost.core.physics.RiderParams
import com.velodromeghost.core.race.RaceConfig
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
                    } else if (now - finishedAt > 3_500_000_000L) {
                        _screen.value = Screen.RESULTS
                        break
                    }
                }
            }
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
        _screen.value = if (st != null && st.phase == RacePhase.FINISHED) Screen.RESULTS else Screen.SETUP
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
