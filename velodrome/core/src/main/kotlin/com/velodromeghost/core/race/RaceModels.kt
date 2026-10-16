package com.velodromeghost.core.race

import com.velodromeghost.core.track.Track
import com.velodromeghost.core.workout.Workout

enum class RaceMode { SPRINT_1V1, OMNIUM_POINTS }

enum class RacePhase { COUNTDOWN, RACING, FINISHED }

enum class Competitor { RIDER, GHOST }

data class RaceConfig(
    val mode: RaceMode = RaceMode.SPRINT_1V1,
    val laps: Int = 3,
    /** Omnium points race: a sprint every N laps, the finish is a double-points sprint. */
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
    /** Positive = ghost starts this far up the track, negative = behind. */
    val ghostHeadStartMeters: Double = 0.0,
    val countdownSeconds: Double = 3.0,
    val track: Track = Track(),
) {
    val ghostFtpWatts: Double get() = riderFtpWatts * ghostStrength
    val raceDistanceMeters: Double get() = laps * track.lapLength

    /** Race distances (m) where points are awarded. The finish is always the last one. Empty for 1v1. */
    fun sprintLines(): List<Double> {
        if (mode != RaceMode.OMNIUM_POINTS) return emptyList()
        val every = sprintEveryLaps.coerceAtLeast(1)
        val lines = (every until laps step every).map { it * track.lapLength }.toMutableList()
        lines += raceDistanceMeters
        return lines
    }

    /** Lines the ghost kicks for (sprints, or just the finish in a 1v1). */
    fun ghostTargetLines(): List<Double> =
        if (mode == RaceMode.OMNIUM_POINTS) sprintLines() else listOf(raceDistanceMeters)

    companion object {
        const val SPRINT_POINTS_FIRST = 5
        const val SPRINT_POINTS_SECOND = 3
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
    val rider: RiderSnapshot,
    val ghost: RiderSnapshot,
    /** Rider distance minus ghost distance: positive = you are ahead. */
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
    /** Once finished: how far ahead (s) the winner was. */
    val marginSeconds: Double?,
) {
    val riderLapsCompleted: Int get() = (rider.distance / lapLength).toInt().coerceIn(0, totalLaps)
    val currentLap: Int get() = (riderLapsCompleted + 1).coerceAtMost(totalLaps)
    val distanceToGo: Double get() = (raceDistanceMeters - rider.distance).coerceAtLeast(0.0)
    val riderTotalPoints: Int get() = riderSprintPoints + riderLapPoints
    val ghostTotalPoints: Int get() = ghostSprintPoints + ghostLapPoints
}
