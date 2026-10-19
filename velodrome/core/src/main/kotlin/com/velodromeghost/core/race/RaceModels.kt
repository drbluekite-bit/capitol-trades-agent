package com.velodromeghost.core.race

import com.velodromeghost.core.track.Track
import com.velodromeghost.core.workout.Workout

enum class RaceMode(val label: String) {
    /** First across the line wins (short, match-sprint style). */
    SPRINT_1V1("1 v 1 race"),

    /** Omnium event: first across the line after a set distance. */
    SCRATCH("Scratch race"),

    /** Omnium event: last rider over the line every 2 laps is out. With one ghost, the first elimination decides it. */
    ELIMINATION("Elimination race"),

    /** Omnium event: 1 point to the first rider over the line every lap (from lap 5), +20 for a lap gain. */
    TEMPO("Tempo race"),

    /** Sprint every N laps (5 / 3 points, double at the finish), +20 for a lap gain. */
    POINTS_RACE("Points race"),

    /** Points race ridden as a two-rider relay: you and a virtual partner swap every few laps. */
    MADISON("Madison"),

    /** Start on opposite sides of the track; best time wins, or catch the ghost to win outright. */
    INDIVIDUAL_PURSUIT("Individual pursuit");

    val isPointsBased: Boolean get() = this == TEMPO || this == POINTS_RACE || this == MADISON
    val isPursuit: Boolean get() = this == INDIVIDUAL_PURSUIT

    /** Bunch races that can be ridden against a whole field of ghosts. */
    val supportsField: Boolean get() = this != SPRINT_1V1 && this != INDIVIDUAL_PURSUIT
}

/** Names (and, in the app, colours) for the ghosts in a field. */
object Field {
    const val MAX_GHOSTS = 11
    private val NAMES = listOf("Cyan", "Lime", "Pink", "Amber", "Violet", "Coral", "Teal", "Gold", "Sky", "Mint", "Rose")

    /** Competitor 0 is you; ghosts are 1..N. */
    fun name(id: Int, fieldSize: Int): String = when {
        id == 0 -> "You"
        fieldSize <= 1 -> "Ghost"
        else -> NAMES[(id - 1) % NAMES.size]
    }
}

enum class RacePhase { COUNTDOWN, RACING, FINISHED }

enum class Competitor { RIDER, GHOST }

data class RaceConfig(
    val mode: RaceMode = RaceMode.SPRINT_1V1,
    val laps: Int = 3,
    /** Points race / Madison: a sprint every N laps, the finish is a double-points sprint. */
    val sprintEveryLaps: Int = 10,
    val riderFtpWatts: Double = 200.0,
    /** Rider + bike. */
    val riderMassKg: Double = 83.0,
    /** Ghost FTP as a multiple of the rider's FTP (1.0 = evenly matched). */
    val ghostStrength: Double = 1.0,
    val ghostMassKg: Double = 83.0,
    /** Pacing for the ghost. When null the ghost holds [constantGhostFraction] of its FTP. */
    val workout: Workout? = null,
    val constantGhostFraction: Double = 0.85,
    /** Ghost goes to [ghostSprintFraction] of its FTP in the last [ghostKickDistanceMeters] before every sprint/finish. */
    val ghostAttacksSprints: Boolean = true,
    val ghostSprintFraction: Double = 1.6,
    val ghostKickDistanceMeters: Double = 200.0,
    /** Positive = ghost starts this far up the track, negative = behind. Ignored for the pursuit. */
    val ghostHeadStartMeters: Double = 0.0,
    /** Madison: laps per turn before the hand-sling. */
    val madisonChangeLaps: Double = 2.0,
    /** Madison: your virtual partner's FTP as a multiple of yours. */
    val partnerStrength: Double = 1.0,
    /** Tempo race: points are awarded from this lap on. */
    val tempoFirstScoringLap: Int = 5,
    /** Number of ghosts (bunch races only; 1v1 and pursuit always have one). */
    val fieldSize: Int = 1,
    /** With a field, each ghost's riding power wanders randomly between these fractions of your FTP. */
    val fieldMinLevel: Double = 0.60,
    val fieldMaxLevel: Double = 1.25,
    /** Seed for the field's random strengths and sprint choices. */
    val seed: Long = 1L,
    /** Elimination race: the last rider over the line every N laps is out. */
    val eliminationEveryLaps: Int = 2,
    val countdownSeconds: Double = 3.0,
    val track: Track = Track(),
) {
    val ghostFtpWatts: Double get() = riderFtpWatts * ghostStrength
    val partnerFtpWatts: Double get() = riderFtpWatts * partnerStrength
    val ghostCount: Int get() = if (mode.supportsField) fieldSize.coerceIn(1, Field.MAX_GHOSTS) else 1

    /** An elimination lasts until one rider is left: one elimination per ghost. */
    val effectiveLaps: Int get() = if (mode == RaceMode.ELIMINATION) eliminationEveryLaps * ghostCount else laps

    val raceDistanceMeters: Double get() = effectiveLaps * track.lapLength

    /** Where the ghost starts: half a lap away in a pursuit, otherwise any head start. */
    val ghostStartMeters: Double get() = if (mode.isPursuit) track.lapLength / 2 else ghostHeadStartMeters

    /** Ghost's finish, in the same distance coordinate as its start. */
    val ghostFinishMeters: Double
        get() = if (mode.isPursuit) raceDistanceMeters + ghostStartMeters else raceDistanceMeters

    /** Race distances (m) where points are awarded. Empty for races decided by the finish only. */
    fun sprintLines(): List<Double> {
        val lap = track.lapLength
        return when (mode) {
            RaceMode.POINTS_RACE, RaceMode.MADISON -> {
                val every = sprintEveryLaps.coerceAtLeast(1)
                (every until effectiveLaps step every).map { it * lap } + raceDistanceMeters
            }
            RaceMode.TEMPO -> {
                val first = tempoFirstScoringLap.coerceIn(1, effectiveLaps)
                (first..effectiveLaps).map { it * lap }
            }
            else -> emptyList()
        }
    }

    /** Elimination race: where a rider is knocked out. The last one is the finish. */
    fun eliminationLines(): List<Double> =
        if (mode != RaceMode.ELIMINATION) emptyList()
        else (1..ghostCount).map { it * eliminationEveryLaps * track.lapLength }

    /** Points for 1st, 2nd, ... at sprint [index] of [count]. */
    fun sprintTable(index: Int, count: Int): IntArray = when {
        mode == RaceMode.TEMPO -> intArrayOf(TEMPO_POINTS)
        index == count - 1 -> SPRINT_TABLE.map { it * 2 }.toIntArray()
        else -> SPRINT_TABLE
    }

    /** Lines the ghosts (and your Madison partner) kick for. */
    fun ghostTargetLines(): List<Double> = when {
        mode.isPursuit -> emptyList()
        mode.isPointsBased -> sprintLines()
        mode == RaceMode.ELIMINATION -> eliminationLines()
        else -> listOf(raceDistanceMeters)
    }

    /** Tempo sprints come every lap, so the ghost's kicks are short. */
    val effectiveKickDistance: Double
        get() = if (mode == RaceMode.TEMPO) minOf(ghostKickDistanceMeters, 80.0) else ghostKickDistanceMeters

    companion object {
        const val SPRINT_POINTS_FIRST = 5
        const val SPRINT_POINTS_SECOND = 3
        /** Points for the first four over a sprint line (5 / 3 / 2 / 1). */
        val SPRINT_TABLE = intArrayOf(5, 3, 2, 1)
        const val TEMPO_POINTS = 1
        const val LAP_GAIN_POINTS = 20
    }
}

data class RiderSnapshot(
    val distance: Double,
    val speedMps: Double,
    val powerWatts: Double,
    val cadenceRpm: Double,
    val crankAngleRad: Double,
    val drafting: Boolean,
    val finishTimeSec: Double?,
) {
    val speedKmh: Double get() = speedMps * 3.6
}

data class SprintResult(
    val number: Int,
    val lap: Int,
    val winner: Competitor,
    val riderPoints: Int,
    /** Points scored by the best-placed ghost. */
    val ghostPoints: Int,
    val isFinal: Boolean,
    /** Competitor ids in the order they took the points. */
    val placings: List<Int> = emptyList(),
    val winnerName: String = "",
    /** Your placing in the sprint, if you were in the points. */
    val riderPlace: Int? = null,
)

data class GhostSnapshot(
    val id: Int,
    val name: String,
    val rider: RiderSnapshot,
    /** Current riding level as a fraction of your FTP (a lone ghost: its strength). */
    val level: Double,
    val eliminated: Boolean,
)

/** One line of the live classification. */
data class Standing(
    val id: Int,
    val name: String,
    val place: Int,
    /** Sprint points + lap points (points races), 0 otherwise. */
    val points: Int,
    val distance: Double,
    val finishTimeSec: Double?,
    val eliminated: Boolean,
) {
    val isYou: Boolean get() = id == 0
}

data class RaceState(
    val mode: RaceMode,
    val phase: RacePhase,
    val countdownRemaining: Double,
    val elapsedSec: Double,
    /** You (in a Madison: your team, whoever of you is racing). */
    val rider: RiderSnapshot,
    /** The single ghost, or in a field the ghost nearest to you on the track. */
    val ghost: RiderSnapshot,
    /**
     * Positive = you are ahead. In a pursuit this compares distance from each rider's own start.
     * In a field it's the gap to the leader (or, if you lead, to second place).
     */
    val gapMeters: Double,
    val gapSeconds: Double,
    val raceDistanceMeters: Double,
    val lapLength: Double,
    val totalLaps: Int,
    val sprints: List<SprintResult>,
    val nextSprintLap: Int?,
    val riderSprintPoints: Int,
    val ghostSprintPoints: Int,
    val riderLapPoints: Int,
    val ghostLapPoints: Int,
    val ghostTargetWatts: Double,
    /** What the workout asks of *you* (scaled to your FTP), used for ERG mode. */
    val riderTargetWatts: Double,
    val targetCadence: Int?,
    val announcement: String?,
    val announcementAtSec: Double,
    val winner: Competitor?,
    /** Once finished: how far ahead (s) the winner was. Null when the race ended by a catch. */
    val marginSeconds: Double?,
    /** Madison: true while your partner is racing (you recover), false on your turn, null otherwise. */
    val partnerActive: Boolean? = null,
    /** Madison: metres until the next hand-sling. */
    val metersToChange: Double? = null,
    val partnerPowerWatts: Double = 0.0,
    /** Pursuit: who caught whom, if anyone. */
    val caughtBy: Competitor? = null,
    /** Every ghost, including eliminated ones. */
    val ghosts: List<GhostSnapshot> = emptyList(),
    /** Live classification, best first. */
    val standings: List<Standing> = emptyList(),
    /** Your place in [standings]. */
    val riderPlace: Int = 1,
    /** Your place on the road (by distance) among riders still racing. */
    val roadPosition: Int = 1,
    val fieldSize: Int = 1,
) {
    val isField: Boolean get() = fieldSize > 1
    val ridersRacing: Int get() = 1 + ghosts.count { !it.eliminated } - if (standings.any { it.isYou && it.eliminated }) 1 else 0
    val riderLapsCompleted: Int get() = (rider.distance / lapLength).toInt().coerceIn(0, totalLaps)
    val currentLap: Int get() = (riderLapsCompleted + 1).coerceAtMost(totalLaps)
    val distanceToGo: Double get() = (raceDistanceMeters - rider.distance).coerceAtLeast(0.0)
    val riderTotalPoints: Int get() = riderSprintPoints + riderLapPoints
    val ghostTotalPoints: Int get() = ghostSprintPoints + ghostLapPoints
}
