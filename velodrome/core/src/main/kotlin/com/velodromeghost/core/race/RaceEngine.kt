package com.velodromeghost.core.race

import com.velodromeghost.core.physics.Physics
import com.velodromeghost.core.physics.RiderParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/**
 * Simulates you vs. the ghost on the track. Call [tick] every frame with your current power.
 * Both riders use the same physics, including drafting, so tactics (sitting on the ghost's
 * wheel and coming round it in the last 200 m) actually work.
 */
class RaceEngine(val config: RaceConfig) {
    private val track = config.track
    private val riderParams = RiderParams(config.riderMassKg)
    private val ghostParams = RiderParams(config.ghostMassKg)
    private val sprintLines = config.sprintLines()
    private val ghostLines = config.ghostTargetLines()
    private val raceDistance = config.raceDistanceMeters

    private var phase = RacePhase.COUNTDOWN
    private var countdown = config.countdownSeconds
    private var elapsed = 0.0

    private var rDist = 0.0
    private var rSpeed = 0.0
    private var rPower = 0.0
    private var rCadence = 0.0
    private var rCrank = 0.0
    private var rDraft = false
    private var rFinish: Double? = null

    private var gDist = config.ghostHeadStartMeters
    private var gSpeed = 0.0
    private var gPower = 0.0
    private var gCadence = 0.0
    private var gCrank = PI / 2
    private var gDraft = false
    private var gFinish: Double? = null

    private var nextSprint = 0
    private val sprints = mutableListOf<SprintResult>()
    private var riderSprintPoints = 0
    private var ghostSprintPoints = 0
    private var lastLapPointsDiff = 0

    private var announcement: String? = null
    private var announcementAt = -100.0
    private var lastAnnouncedLap = 0
    private var ghostFinishAnnounced = false

    private var winner: Competitor? = null
    private var margin: Double? = null

    val isFinished: Boolean get() = phase == RacePhase.FINISHED

    fun tick(dt: Double, riderPowerWatts: Double, riderCadenceRpm: Double?): RaceState {
        val step = dt.coerceIn(0.0, 0.25)
        rPower = max(0.0, riderPowerWatts)
        when (phase) {
            RacePhase.COUNTDOWN -> {
                countdown -= step
                rCadence = riderCadenceRpm ?: 0.0
                if (countdown <= 0.0) {
                    phase = RacePhase.RACING
                    countdown = 0.0
                    announce("GO!")
                }
            }
            RacePhase.RACING -> {
                val before = elapsed
                var remaining = step
                while (remaining > 1e-9 && phase == RacePhase.RACING) {
                    val h = minOf(SUBSTEP, remaining)
                    substep(h, riderCadenceRpm)
                    remaining -= h
                }
                config.workout?.textEventsIn(before, elapsed)?.lastOrNull()?.let { announce(it.message) }
            }
            RacePhase.FINISHED -> Unit
        }
        return snapshot()
    }

    private fun substep(h: Double, riderCadence: Double?) {
        elapsed += h
        gPower = ghostPowerAt()

        val ghostAhead = track.wrappedDelta(rDist, gDist)
        rDraft = ghostAhead > 0 && ghostAhead < Physics.DRAFT_RANGE_METERS
        gDraft = -ghostAhead > 0 && -ghostAhead < Physics.DRAFT_RANGE_METERS
        val rFactor = Physics.draftFactor(ghostAhead)
        val gFactor = Physics.draftFactor(-ghostAhead)

        val prevR = rDist
        val prevG = gDist
        rSpeed = Physics.step(rSpeed, rPower, riderParams, rFactor, h)
        gSpeed = Physics.step(gSpeed, gPower, ghostParams, gFactor, h)
        rDist += rSpeed * h
        gDist += gSpeed * h

        rCadence = riderCadence ?: Physics.cadenceForSpeed(rSpeed)
        gCadence = Physics.cadenceForSpeed(gSpeed)
        rCrank = (rCrank + rCadence / 60.0 * 2 * PI * h) % (2 * PI)
        gCrank = (gCrank + gCadence / 60.0 * 2 * PI * h) % (2 * PI)

        checkSprints(prevR, prevG)
        checkLapGains()
        checkLapAnnouncements()
        checkFinish(prevR, prevG, h)
    }

    private fun ghostPowerAt(): Double {
        val ftp = config.ghostFtpWatts
        if (gFinish != null) return 0.45 * ftp
        val base = config.workout?.targetWatts(elapsed, ftp) ?: (config.constantGhostFraction * ftp)
        if (config.ghostAttacksSprints) {
            val line = ghostLines.firstOrNull { it > gDist }
            if (line != null && line - gDist <= config.ghostKickDistanceMeters) {
                return max(base, config.ghostSprintFraction * ftp)
            }
        }
        return base
    }

    /** Fraction of the substep at which a rider moving prev -> now crossed [line]. */
    private fun crossFraction(prev: Double, now: Double, line: Double): Double =
        if (now - prev <= 1e-9) 0.0 else ((line - prev) / (now - prev)).coerceIn(0.0, 1.0)

    private fun checkSprints(prevR: Double, prevG: Double) {
        while (nextSprint < sprintLines.size) {
            val line = sprintLines[nextSprint]
            val rCrossed = rDist >= line
            val gCrossed = gDist >= line
            if (!rCrossed && !gCrossed) return
            val sprintWinner = when {
                rCrossed && gCrossed ->
                    if (crossFraction(prevR, rDist, line) <= crossFraction(prevG, gDist, line)) Competitor.RIDER
                    else Competitor.GHOST
                rCrossed -> Competitor.RIDER
                else -> Competitor.GHOST
            }
            val isFinal = nextSprint == sprintLines.lastIndex
            val mult = if (isFinal) 2 else 1
            val first = RaceConfig.SPRINT_POINTS_FIRST * mult
            val second = RaceConfig.SPRINT_POINTS_SECOND * mult
            val result = SprintResult(
                number = nextSprint + 1,
                lap = (line / track.lapLength).toInt(),
                winner = sprintWinner,
                riderPoints = if (sprintWinner == Competitor.RIDER) first else second,
                ghostPoints = if (sprintWinner == Competitor.GHOST) first else second,
                isFinal = isFinal,
            )
            sprints += result
            riderSprintPoints += result.riderPoints
            ghostSprintPoints += result.ghostPoints
            val label = if (isFinal) "Final sprint" else "Sprint ${result.number}"
            announce(
                if (sprintWinner == Competitor.RIDER) "$label: YOU take it! +$first"
                else "$label: ghost takes it. +$second for you"
            )
            nextSprint++
        }
    }

    private fun lapPointsDiff(): Int {
        if (config.mode != RaceMode.OMNIUM_POINTS) return 0
        val laps = (rDist - gDist) / track.lapLength
        return if (laps >= 0) floor(laps).toInt() else -floor(-laps).toInt()
    }

    private fun checkLapGains() {
        val diff = lapPointsDiff()
        if (diff != lastLapPointsDiff) {
            if (diff > lastLapPointsDiff) {
                announce(if (diff > 0) "You lapped the ghost! +${RaceConfig.LAP_GAIN_POINTS}" else "You got the lap back!")
            } else {
                announce(if (diff < 0) "The ghost lapped you! Ghost +${RaceConfig.LAP_GAIN_POINTS}" else "Ghost got the lap back")
            }
            lastLapPointsDiff = diff
        }
    }

    private fun checkLapAnnouncements() {
        val lapsDone = (rDist / track.lapLength).toInt()
        if (lapsDone <= lastAnnouncedLap || rFinish != null) return
        lastAnnouncedLap = lapsDone
        val toGo = config.laps - lapsDone
        when {
            toGo == 1 -> announce("BELL - final lap!")
            toGo <= 0 -> Unit
            config.mode == RaceMode.OMNIUM_POINTS -> {
                val nextLine = sprintLines.getOrNull(nextSprint)
                val lapsToSprint = nextLine?.let { ((it - lapsDone * track.lapLength) / track.lapLength).toInt() }
                when (lapsToSprint) {
                    1 -> announce("Bell - sprint next lap!")
                    else -> announce("$toGo laps to go")
                }
            }
            else -> announce("$toGo laps to go")
        }
    }

    private fun checkFinish(prevR: Double, prevG: Double, h: Double) {
        if (gFinish == null && gDist >= raceDistance) {
            gFinish = elapsed - h + h * crossFraction(prevG, gDist, raceDistance)
        }
        if (rFinish == null && rDist >= raceDistance) {
            rFinish = elapsed - h + h * crossFraction(prevR, rDist, raceDistance)
        }
        if (gFinish != null && rFinish == null && !ghostFinishAnnounced && config.mode == RaceMode.SPRINT_1V1) {
            ghostFinishAnnounced = true
            announce("Ghost has crossed the line - keep going!")
        }
        val rTime = rFinish ?: return
        phase = RacePhase.FINISHED
        val gTime = gFinish
        val ghostMargin = gTime?.let { rTime - it }
            ?: -((raceDistance - gDist) / max(gSpeed, 1.0))
        when (config.mode) {
            RaceMode.SPRINT_1V1 -> {
                winner = if (gTime != null && gTime < rTime) Competitor.GHOST else Competitor.RIDER
                margin = abs(ghostMargin)
            }
            RaceMode.OMNIUM_POINTS -> {
                val rTotal = riderSprintPoints + riderLapPoints()
                val gTotal = ghostSprintPoints + ghostLapPoints()
                winner = when {
                    rTotal > gTotal -> Competitor.RIDER
                    gTotal > rTotal -> Competitor.GHOST
                    else -> sprints.lastOrNull()?.winner ?: Competitor.RIDER
                }
                margin = abs(ghostMargin)
            }
        }
        announce(if (winner == Competitor.RIDER) "You win!" else "The ghost wins")
    }

    private fun riderLapPoints() = max(0, lapPointsDiff()) * RaceConfig.LAP_GAIN_POINTS
    private fun ghostLapPoints() = max(0, -lapPointsDiff()) * RaceConfig.LAP_GAIN_POINTS

    private fun announce(message: String) {
        announcement = message
        announcementAt = elapsed
    }

    private fun snapshot(): RaceState {
        val gap = rDist - gDist
        val gapSec = if (gap >= 0) gap / max(gSpeed, 1.0) else gap / max(rSpeed, 1.0)
        val riderTarget = config.workout?.targetWatts(elapsed, config.riderFtpWatts)
            ?: (config.constantGhostFraction * config.riderFtpWatts)
        return RaceState(
            mode = config.mode,
            phase = phase,
            countdownRemaining = countdown,
            elapsedSec = elapsed,
            rider = RiderSnapshot(rDist, rSpeed, rPower, rCadence, rCrank, rDraft, rFinish),
            ghost = RiderSnapshot(gDist, gSpeed, gPower, gCadence, gCrank, gDraft, gFinish),
            gapMeters = gap,
            gapSeconds = gapSec,
            raceDistanceMeters = raceDistance,
            lapLength = track.lapLength,
            totalLaps = config.laps,
            sprints = sprints.toList(),
            nextSprintLap = sprintLines.getOrNull(nextSprint)?.let { (it / track.lapLength).toInt() },
            riderSprintPoints = riderSprintPoints,
            ghostSprintPoints = ghostSprintPoints,
            riderLapPoints = riderLapPoints(),
            ghostLapPoints = ghostLapPoints(),
            ghostTargetWatts = if (phase == RacePhase.RACING) gPower else ghostPowerAt(),
            riderTargetWatts = riderTarget,
            targetCadence = config.workout?.cadenceAt(elapsed),
            announcement = announcement,
            announcementAtSec = announcementAt,
            winner = winner,
            marginSeconds = margin,
        )
    }

    private companion object {
        const val SUBSTEP = 0.02
    }
}
