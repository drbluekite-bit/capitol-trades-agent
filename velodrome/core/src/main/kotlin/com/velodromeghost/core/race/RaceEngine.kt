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
    private val lap = track.lapLength
    private val mode = config.mode
    private val riderParams = RiderParams(config.riderMassKg)
    private val ghostParams = RiderParams(config.ghostMassKg)
    private val sprintLines = config.sprintLines()
    private val ghostLines = config.ghostTargetLines()
    private val raceDistance = config.raceDistanceMeters
    private val ghostStart = config.ghostStartMeters
    private val ghostFinishLine = config.ghostFinishMeters
    private val changeMeters = config.madisonChangeLaps.coerceAtLeast(0.5) * lap

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

    private var gDist = ghostStart
    private var gSpeed = 0.0
    private var gPower = 0.0
    private var gCadence = 0.0
    private var gCrank = PI / 2
    private var gDraft = false
    private var gFinish: Double? = null

    private var partnerActive = false
    private var partnerPower = 0.0

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
    private var caughtBy: Competitor? = null

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
                    announce(if (mode == RaceMode.MADISON) "GO! Your turn first" else "GO!")
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
        partnerPower = if (mode == RaceMode.MADISON) partnerPowerAt() else 0.0

        // Nobody drafts in a pursuit: you're half a lap apart until someone is caught.
        val ghostAhead = if (mode.isPursuit) 0.0 else track.wrappedDelta(rDist, gDist)
        rDraft = ghostAhead > 0 && ghostAhead < Physics.DRAFT_RANGE_METERS
        gDraft = -ghostAhead > 0 && -ghostAhead < Physics.DRAFT_RANGE_METERS
        val rFactor = Physics.draftFactor(ghostAhead)
        val gFactor = Physics.draftFactor(-ghostAhead)

        val teamPower = if (partnerActive) partnerPower else rPower
        val prevR = rDist
        val prevG = gDist
        rSpeed = Physics.step(rSpeed, teamPower, riderParams, rFactor, h)
        gSpeed = Physics.step(gSpeed, gPower, ghostParams, gFactor, h)
        rDist += rSpeed * h
        gDist += gSpeed * h

        rCadence = riderCadence ?: if (partnerActive) 0.0 else Physics.cadenceForSpeed(rSpeed)
        gCadence = Physics.cadenceForSpeed(gSpeed)
        rCrank = (rCrank + rCadence / 60.0 * 2 * PI * h) % (2 * PI)
        gCrank = (gCrank + gCadence / 60.0 * 2 * PI * h) % (2 * PI)

        checkMadisonChange()
        checkSprints(prevR, prevG)
        checkLapGains()
        checkLapAnnouncements()
        if (mode.isPursuit && gFinish == null) checkCatch()
        if (phase == RacePhase.RACING) checkFinish(prevR, prevG, h)
    }

    /** Ghost's FTP-scaled power for this moment of the race. */
    private fun ghostPowerAt(): Double {
        val ftp = config.ghostFtpWatts
        if (gFinish != null) return 0.45 * ftp
        val base = config.workout?.targetWatts(elapsed, ftp) ?: (config.constantGhostFraction * ftp)
        return if (config.ghostAttacksSprints) kick(base, ftp, gDist) else base
    }

    /** Your virtual Madison partner rides the same pacing, and always contests the sprints. */
    private fun partnerPowerAt(): Double {
        val ftp = config.partnerFtpWatts
        val base = config.workout?.targetWatts(elapsed, ftp) ?: (config.constantGhostFraction * ftp)
        return kick(base, ftp, rDist)
    }

    private fun kick(base: Double, ftp: Double, position: Double): Double {
        val line = ghostLines.firstOrNull { it > position } ?: return base
        return if (line - position <= config.effectiveKickDistance) max(base, config.ghostSprintFraction * ftp) else base
    }

    private fun checkMadisonChange() {
        if (mode != RaceMode.MADISON) return
        val nowPartner = floor(rDist / changeMeters).toInt() % 2 == 1
        if (nowPartner != partnerActive) {
            partnerActive = nowPartner
            // The hand-sling throws the incoming rider into the race.
            rSpeed += SLING_BOOST_MPS
            announce(if (partnerActive) "Hand-sling! Partner's turn - recover" else "Hand-sling! YOUR TURN - go!")
        }
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
            val (first, second) = config.sprintPoints(nextSprint, sprintLines.size)
            val result = SprintResult(
                number = nextSprint + 1,
                lap = (line / lap).toInt(),
                winner = sprintWinner,
                riderPoints = if (sprintWinner == Competitor.RIDER) first else second,
                ghostPoints = if (sprintWinner == Competitor.GHOST) first else second,
                isFinal = isFinal,
            )
            sprints += result
            riderSprintPoints += result.riderPoints
            ghostSprintPoints += result.ghostPoints
            val team = if (mode == RaceMode.MADISON) "your team" else "YOU"
            announce(
                when {
                    mode == RaceMode.TEMPO ->
                        if (sprintWinner == Competitor.RIDER) "Lap ${result.lap}: point to you!" else "Lap ${result.lap}: point to the ghost"
                    sprintWinner == Competitor.RIDER ->
                        "${if (isFinal) "Final sprint" else "Sprint ${result.number}"}: $team take it! +$first"
                    else ->
                        "${if (isFinal) "Final sprint" else "Sprint ${result.number}"}: ghost takes it. +$second for $team"
                }
            )
            nextSprint++
        }
    }

    private fun lapPointsDiff(): Int {
        if (!mode.isPointsBased) return 0
        val laps = (rDist - gDist) / lap
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
        val lapsDone = (rDist / lap).toInt()
        if (lapsDone <= lastAnnouncedLap || rFinish != null) return
        lastAnnouncedLap = lapsDone
        val toGo = config.laps - lapsDone
        val nextLine = sprintLines.getOrNull(nextSprint)
        val lapsToSprint = nextLine?.let { ((it - lapsDone * lap) / lap).toInt() }
        when {
            toGo == 1 -> announce("BELL - final lap!")
            toGo <= 0 -> Unit
            mode == RaceMode.TEMPO && lapsToSprint == 1 && sprints.isEmpty() -> announce("Points every lap from now!")
            (mode == RaceMode.POINTS_RACE || mode == RaceMode.MADISON) && lapsToSprint == 1 ->
                announce("Bell - sprint next lap!")
            else -> announce("$toGo laps to go")
        }
    }

    private fun checkCatch() {
        val caught = when {
            rDist >= gDist -> Competitor.RIDER
            gDist - rDist >= lap -> Competitor.GHOST
            else -> return
        }
        caughtBy = caught
        winner = caught
        margin = null
        phase = RacePhase.FINISHED
        if (caught == Competitor.RIDER) rFinish = elapsed
        announce(if (caught == Competitor.RIDER) "You caught the ghost! You win!" else "The ghost caught you")
    }

    private fun checkFinish(prevR: Double, prevG: Double, h: Double) {
        if (gFinish == null && gDist >= ghostFinishLine) {
            gFinish = elapsed - h + h * crossFraction(prevG, gDist, ghostFinishLine)
        }
        if (rFinish == null && rDist >= raceDistance) {
            rFinish = elapsed - h + h * crossFraction(prevR, rDist, raceDistance)
        }
        if (gFinish != null && rFinish == null && !ghostFinishAnnounced && !mode.isPointsBased) {
            ghostFinishAnnounced = true
            announce("Ghost has crossed the line - keep going!")
        }
        val rTime = rFinish ?: return
        phase = RacePhase.FINISHED
        val gTime = gFinish
        val ghostMargin = gTime?.let { rTime - it }
            ?: -((ghostFinishLine - gDist) / max(gSpeed, 1.0))
        val ghostFirst = gTime != null && gTime < rTime
        winner = if (mode.isPointsBased) {
            val rTotal = riderSprintPoints + riderLapPoints()
            val gTotal = ghostSprintPoints + ghostLapPoints()
            when {
                rTotal > gTotal -> Competitor.RIDER
                gTotal > rTotal -> Competitor.GHOST
                // Tie: decided by the finish placing.
                else -> if (ghostFirst) Competitor.GHOST else Competitor.RIDER
            }
        } else {
            if (ghostFirst) Competitor.GHOST else Competitor.RIDER
        }
        margin = abs(ghostMargin)
        announce(if (winner == Competitor.RIDER) "You win!" else "The ghost wins")
    }

    private fun riderLapPoints() = max(0, lapPointsDiff()) * RaceConfig.LAP_GAIN_POINTS
    private fun ghostLapPoints() = max(0, -lapPointsDiff()) * RaceConfig.LAP_GAIN_POINTS

    private fun announce(message: String) {
        announcement = message
        announcementAt = elapsed
    }

    private fun snapshot(): RaceState {
        val gap = if (mode.isPursuit) rDist - (gDist - ghostStart) else rDist - gDist
        val gapSec = if (gap >= 0) gap / max(gSpeed, 1.0) else gap / max(rSpeed, 1.0)
        val riderTarget = when {
            partnerActive -> 0.5 * config.riderFtpWatts
            else -> config.workout?.targetWatts(elapsed, config.riderFtpWatts)
                ?: (config.constantGhostFraction * config.riderFtpWatts)
        }
        val madison = mode == RaceMode.MADISON
        return RaceState(
            mode = mode,
            phase = phase,
            countdownRemaining = countdown,
            elapsedSec = elapsed,
            rider = RiderSnapshot(rDist, rSpeed, rPower, rCadence, rCrank, rDraft, rFinish),
            ghost = RiderSnapshot(gDist, gSpeed, gPower, gCadence, gCrank, gDraft, gFinish),
            gapMeters = gap,
            gapSeconds = gapSec,
            raceDistanceMeters = raceDistance,
            lapLength = lap,
            totalLaps = config.laps,
            sprints = sprints.toList(),
            nextSprintLap = sprintLines.getOrNull(nextSprint)?.let { (it / lap).toInt() },
            riderSprintPoints = riderSprintPoints,
            ghostSprintPoints = ghostSprintPoints,
            riderLapPoints = riderLapPoints(),
            ghostLapPoints = ghostLapPoints(),
            ghostTargetWatts = if (phase == RacePhase.RACING) gPower else ghostPowerAt(),
            riderTargetWatts = riderTarget,
            targetCadence = if (partnerActive) null else config.workout?.cadenceAt(elapsed),
            announcement = announcement,
            announcementAtSec = announcementAt,
            winner = winner,
            marginSeconds = margin,
            partnerActive = if (madison) partnerActive else null,
            metersToChange = if (madison) changeMeters - (rDist % changeMeters) else null,
            partnerPowerWatts = partnerPower,
            caughtBy = caughtBy,
        )
    }

    private companion object {
        const val SUBSTEP = 0.02
        const val SLING_BOOST_MPS = 0.8
    }
}
