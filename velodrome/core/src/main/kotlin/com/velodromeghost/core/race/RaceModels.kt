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
    val countdownSeconds: Double = 3.0,
    val track: Track = Track(),
) {
    val ghostFtpWatts: Double get() = riderFtpWatts * ghostStrength
    val partnerFtpWatts: Double get() = riderFtpWatts * partnerStrength
    val raceDistanceMeters: Double get() = laps * track.lapLength

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
                (every until laps step every).map { it * lap } + raceDistanceMeters
            }
            RaceMode.TEMPO -> {
                val first = tempoFirstScoringLap.coerceIn(1, laps)
                (first..laps).map { it * lap }
            }
            else -> emptyList()
        }
    }

    /** (winner's points, other's points) for sprint [index] of [count]. */
    fun sprintPoints(index: Int, count: Int): Pair<Int, Int> = when {
        mode == RaceMode.TEMPO -> TEMPO_POINTS to 0
        index == count - 1 -> SPRINT_POINTS_FIRST * 2 to SPRINT_POINTS_SECOND * 2
        else -> SPRINT_POINTS_FIRST to SPRINT_POINTS_SECOND
    }

    /** Lines the ghost (and your Madison partner) kick for. */
    fun ghostTargetLines(): List<Double> = when {
        mode.isPursuit -> emptyList()
        mode.isPointsBased -> sprintLines()
        else -> listOf(raceDistanceMeters)
    }

    /** Tempo sprints come every lap, so the ghost's kicks are short. */
    val effectiveKickDistance: Double
        get() = if (mode == RaceMode.TEMPO) minOf(ghostKickDistanceMeters, 80.0) else ghostKickDistanceMeters

    companion object {
        const val SPRINT_POINTS_FIRST = 5
        const val SPRINT_POINTS_SECOND = 3
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
    val ghostPoints: Int,
    val isFinal: Boolean,
)

data class RaceState(
    val mode: RaceMode,
    val phase: RacePhase,
    val countdownRemaining: Double,
    val elapsedSec: Double,
    /** You (in a Madison: your team, whoever of you is racing). */
    val rider: RiderSnapshot,
    val ghost: RiderSnapshot,
    /** Positive = you are ahead. In a pursuit this compares distance from each rider's own start. */
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
) {
    val riderLapsCompleted: Int get() = (rider.distance / lapLength).toInt().coerceIn(0, totalLaps)
    val currentLap: Int get() = (riderLapsCompleted + 1).coerceAtMost(totalLaps)
    val distanceToGo: Double get() = (raceDistanceMeters - rider.distance).coerceAtLeast(0.0)
    val riderTotalPoints: Int get() = riderSprintPoints + riderLapPoints
    val ghostTotalPoints: Int get() = ghostSprintPoints + ghostLapPoints
}
