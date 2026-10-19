package com.velodromeghost.core.race

import com.velodromeghost.core.physics.Physics
import com.velodromeghost.core.physics.RiderParams
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Simulates you against one ghost or a whole field of them. Call [tick] every frame with your
 * current power. Everyone uses the same physics, including drafting, so tactics (sitting on a
 * wheel and coming round it in the last 200 m) actually work.
 *
 * In a field, every ghost rides at a power that wanders randomly between
 * [RaceConfig.fieldMinLevel] and [RaceConfig.fieldMaxLevel] of your FTP during the race, each has
 * its own sprint, and they don't all contest every sprint.
 */
class RaceEngine(val config: RaceConfig) {
    private val track = config.track
    private val lap = track.lapLength
    private val mode = config.mode
    private val ghostCount = config.ghostCount
    private val isField = ghostCount > 1
    private val sprintLines = config.sprintLines()
    private val elimLines = config.eliminationLines()
    private val kickLines = config.ghostTargetLines()
    private val raceDistance = config.raceDistanceMeters
    private val ghostStart = if (isField) 0.0 else config.ghostStartMeters
    private val ghostFinishLine = if (isField) raceDistance else config.ghostFinishMeters
    private val changeMeters = config.madisonChangeLaps.coerceAtLeast(0.5) * lap
    private val rng = Random(config.seed)
    private val minLevel = min(config.fieldMinLevel, config.fieldMaxLevel)
    private val maxLevel = max(config.fieldMinLevel, config.fieldMaxLevel)

    private inner class Racer(val id: Int, massKg: Double) {
        val params = RiderParams(massKg)
        val name = Field.name(id, ghostCount)
        val isYou get() = id == 0
        var dist = 0.0
        var prevDist = 0.0
        var speed = 0.0
        var power = 0.0
        var cadence = 0.0
        var crank = 0.0
        var drafting = false
        var dragFactor = 1.0
        var finish: Double? = null
        var eliminated = false
        var eliminatedPlace = 0
        var sprintPoints = 0

        // Ghost personality
        var level = 1.0
        var levelFrom = 1.0
        var levelTo = 1.0
        var rampStart = 0.0
        var nextLevelChange = Double.MAX_VALUE
        var sprintSkill = 1.0
        var kickLineIndex = -1
        var contesting = true

        val active get() = !eliminated
    }

    private val you = Racer(0, config.riderMassKg)
    private val ghosts = (1..ghostCount).map { id ->
        Racer(id, config.ghostMassKg).apply {
            dist = ghostStart
            crank = PI / 2 * id
            if (isField) {
                level = randomLevel()
                levelFrom = level
                levelTo = level
                nextLevelChange = rng.nextDouble(10.0, 60.0)
                sprintSkill = rng.nextDouble(0.85, 1.15)
            } else {
                level = config.ghostStrength
            }
        }
    }
    private val everyone = listOf(you) + ghosts
    private val firstGhost = ghosts.first()

    private var phase = RacePhase.COUNTDOWN
    private var countdown = config.countdownSeconds
    private var elapsed = 0.0
    private var riderPower = 0.0

    private var partnerActive = false
    private var partnerPower = 0.0

    private class OpenSprint(val index: Int, val line: Double) {
        val order = mutableListOf<Int>()
    }

    private var nextSprintToOpen = 0
    private val openSprints = mutableListOf<OpenSprint>()
    private val sprints = mutableListOf<SprintResult>()
    private var nextElimination = 0
    private var lastLapDiff = 0

    private var announcement: String? = null
    private var announcementAt = -100.0
    private var lastAnnouncedLap = 0
    private var ghostFinishAnnounced = false

    private var winnerId: Int? = null
    private var margin: Double? = null
    private var caughtBy: Competitor? = null

    val isFinished: Boolean get() = phase == RacePhase.FINISHED

    fun tick(dt: Double, riderPowerWatts: Double, riderCadenceRpm: Double?): RaceState {
        val step = dt.coerceIn(0.0, 0.25)
        riderPower = max(0.0, riderPowerWatts)
        when (phase) {
            RacePhase.COUNTDOWN -> {
                countdown -= step
                you.cadence = riderCadenceRpm ?: 0.0
                you.power = riderPower
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
            RacePhase.FINISHED -> you.power = riderPower
        }
        return snapshot()
    }

    // ------------------------------------------------------------------ simulation

    private fun substep(h: Double, riderCadence: Double?) {
        elapsed += h
        for (g in ghosts) {
            if (!g.active) continue
            if (isField) updateLevel(g)
            g.power = ghostPower(g)
        }
        partnerPower = if (mode == RaceMode.MADISON) partnerPowerNow() else 0.0
        you.power = riderPower

        val racing = everyone.filter { it.active }
        for (r in racing) {
            if (mode.isPursuit) {
                r.drafting = false
                r.dragFactor = 1.0
                continue
            }
            // Nearest wheel ahead within drafting range.
            var nearest = Double.MAX_VALUE
            for (o in racing) {
                if (o === r) continue
                val d = track.wrappedDelta(r.dist, o.dist)
                if (d > 0 && d < nearest) nearest = d
            }
            r.drafting = nearest < Physics.DRAFT_RANGE_METERS
            r.dragFactor = Physics.draftFactor(nearest)
        }

        for (r in racing) {
            r.prevDist = r.dist
            val p = if (r.isYou && partnerActive) partnerPower else r.power
            r.speed = Physics.step(r.speed, p, r.params, r.dragFactor, h)
            r.dist += r.speed * h
            r.cadence = if (r.isYou) riderCadence ?: if (partnerActive) 0.0 else Physics.cadenceForSpeed(r.speed)
            else Physics.cadenceForSpeed(r.speed)
            r.crank = (r.crank + r.cadence / 60.0 * 2 * PI * h) % (2 * PI)
        }

        checkMadisonChange()
        checkSprints()
        checkEliminations()
        if (phase != RacePhase.RACING) return
        checkLapGains()
        checkLapAnnouncements()
        if (mode.isPursuit && firstGhost.finish == null) checkCatch()
        if (phase == RacePhase.RACING && mode != RaceMode.ELIMINATION) checkFinish(h)
    }

    private fun randomLevel() = if (maxLevel - minLevel < 1e-6) minLevel else rng.nextDouble(minLevel, maxLevel)

    /** Each ghost drifts to a new random strength every 20-75 s, easing over ~8 s. */
    private fun updateLevel(g: Racer) {
        if (elapsed >= g.nextLevelChange) {
            g.levelFrom = g.level
            g.levelTo = randomLevel()
            g.rampStart = elapsed
            g.nextLevelChange = elapsed + rng.nextDouble(20.0, 75.0)
        }
        val t = ((elapsed - g.rampStart) / LEVEL_RAMP_SEC).coerceIn(0.0, 1.0)
        g.level = g.levelFrom + (g.levelTo - g.levelFrom) * t
    }

    private fun ghostPower(g: Racer): Double {
        // A field rider's wandering level *is* its riding power (fraction of your FTP).
        // A lone ghost rides its pacing (workout or constant %) scaled by its strength.
        val ftp = if (isField) config.riderFtpWatts else config.riderFtpWatts * g.level
        if (g.finish != null) return 0.45 * ftp
        val base = when {
            isField -> g.level * ftp
            else -> config.workout?.targetWatts(elapsed, ftp) ?: (config.constantGhostFraction * ftp)
        }
        if (!config.ghostAttacksSprints) return base
        val idx = kickLines.indexOfFirst { it > g.dist }
        if (idx < 0 || kickLines[idx] - g.dist > config.effectiveKickDistance) return base
        if (g.kickLineIndex != idx) {
            g.kickLineIndex = idx
            g.contesting = !isField || idx == kickLines.lastIndex || rng.nextDouble() < contestProbability()
        }
        return if (g.contesting) max(base, config.ghostSprintFraction * g.sprintSkill * ftp) else base
    }

    private fun contestProbability() = when (mode) {
        RaceMode.TEMPO -> 0.35
        RaceMode.ELIMINATION -> 0.85
        else -> 0.6
    }

    /** Your virtual Madison partner rides the same pacing, and always contests the sprints. */
    private fun partnerPowerNow(): Double {
        val ftp = config.partnerFtpWatts
        val base = config.workout?.targetWatts(elapsed, ftp) ?: (config.constantGhostFraction * ftp)
        val line = kickLines.firstOrNull { it > you.dist } ?: return base
        return if (line - you.dist <= config.effectiveKickDistance) max(base, config.ghostSprintFraction * ftp) else base
    }

    private fun checkMadisonChange() {
        if (mode != RaceMode.MADISON) return
        val nowPartner = (you.dist / changeMeters).toInt() % 2 == 1
        if (nowPartner != partnerActive) {
            partnerActive = nowPartner
            // The hand-sling throws the incoming rider into the race.
            you.speed += SLING_BOOST_MPS
            announce(if (partnerActive) "Hand-sling! Partner's turn - recover" else "Hand-sling! YOUR TURN - go!")
        }
    }

    /** Fraction of the substep at which a rider moving prev -> now crossed [line]. */
    private fun crossFraction(r: Racer, line: Double): Double =
        if (r.dist - r.prevDist <= 1e-9) 0.0 else ((line - r.prevDist) / (r.dist - r.prevDist)).coerceIn(0.0, 1.0)

    // ------------------------------------------------------------------ sprints

    private fun checkSprints() {
        while (nextSprintToOpen < sprintLines.size && everyone.any { it.active && it.dist >= sprintLines[nextSprintToOpen] }) {
            openSprints += OpenSprint(nextSprintToOpen, sprintLines[nextSprintToOpen])
            nextSprintToOpen++
        }
        val racingCount = everyone.count { it.active }
        for (s in openSprints.toList()) {
            val crossers = everyone
                .filter { it.active && it.id !in s.order && it.dist >= s.line }
                .sortedBy { crossFraction(it, s.line) }
            s.order += crossers.map { it.id }
            val table = config.sprintTable(s.index, sprintLines.size)
            if (s.order.size >= min(table.size, racingCount)) closeSprint(s)
        }
    }

    private fun closeSprint(s: OpenSprint) {
        openSprints.remove(s)
        val table = config.sprintTable(s.index, sprintLines.size)
        val placed = s.order.take(table.size)
        val points = placed.withIndex().associate { (i, id) -> id to table[i] }
        points.forEach { (id, pts) -> racer(id).sprintPoints += pts }
        val winner = placed.first()
        val riderPlace = placed.indexOf(0).takeIf { it >= 0 }?.plus(1)
        val isFinal = s.index == sprintLines.lastIndex
        val result = SprintResult(
            number = s.index + 1,
            lap = (s.line / lap).toInt(),
            winner = if (winner == 0) Competitor.RIDER else Competitor.GHOST,
            riderPoints = points[0] ?: 0,
            ghostPoints = points.filterKeys { it != 0 }.values.maxOrNull() ?: 0,
            isFinal = isFinal,
            placings = placed,
            winnerName = racer(winner).name,
            riderPlace = riderPlace,
        )
        sprints += result
        if (phase == RacePhase.RACING) announce(sprintMessage(result))
    }

    private fun sprintMessage(r: SprintResult): String {
        val label = when {
            mode == RaceMode.TEMPO -> "Lap ${r.lap}"
            r.isFinal -> "Final sprint"
            else -> "Sprint ${r.number}"
        }
        val team = if (mode == RaceMode.MADISON) "your team" else "you"
        return when {
            r.winner == Competitor.RIDER -> if (mode == RaceMode.TEMPO) "$label: point to you!" else "$label: ${team.uppercase()} take it! +${r.riderPoints}"
            mode == RaceMode.TEMPO -> "$label: point to ${r.winnerName}"
            !isField -> "$label: ghost takes it. +${r.riderPoints} for $team"
            r.riderPlace != null -> "$label: ${r.winnerName} wins, $team ${ordinal(r.riderPlace)} (+${r.riderPoints})"
            else -> "$label: ${r.winnerName} wins, $team out of the points"
        }
    }

    // ------------------------------------------------------------------ eliminations

    private fun checkEliminations() {
        if (mode != RaceMode.ELIMINATION) return
        while (nextElimination < elimLines.size && phase == RacePhase.RACING) {
            val line = elimLines[nextElimination]
            val racing = everyone.filter { it.active }
            val crossed = racing.filter { it.dist >= line }
            if (crossed.size < racing.size - 1) return
            val last = if (crossed.size == racing.size) crossed.maxBy { crossFraction(it, line) }
            else racing.first { it.dist < line }
            last.eliminated = true
            last.eliminatedPlace = racing.size
            last.finish = elapsed
            nextElimination++
            val remaining = racing.filter { it !== last }
            when {
                last.isYou -> {
                    announce("You're eliminated - ${ordinal(racing.size)} of ${everyone.size}")
                    endRace()
                }
                remaining.size == 1 -> {
                    remaining.first().finish = elapsed
                    announce(if (remaining.first().isYou) "Last one standing - YOU WIN!" else "${remaining.first().name} wins")
                    endRace()
                }
                else -> announce("${last.name} is eliminated - ${remaining.size} left")
            }
        }
    }

    // ------------------------------------------------------------------ laps and points

    /** Laps gained (+) or lost (-) on the rest of the field (the median of the others). */
    private fun lapDiff(r: Racer): Int {
        if (!mode.isPointsBased) return 0
        val others = everyone.filter { it.active && it !== r }.map { it.dist }.sorted()
        if (others.isEmpty()) return 0
        val mid = others.size / 2
        val median = if (others.size % 2 == 1) others[mid] else (others[mid - 1] + others[mid]) / 2
        val laps = (r.dist - median) / lap
        return if (laps >= 0) laps.toInt() else -((-laps).toInt())
    }

    private fun lapPoints(r: Racer) = lapDiff(r) * RaceConfig.LAP_GAIN_POINTS
    private fun totalPoints(r: Racer) = r.sprintPoints + lapPoints(r)

    private fun checkLapGains() {
        val diff = lapDiff(you)
        if (diff == lastLapDiff) return
        val field = if (isField) "the field" else "the ghost"
        announce(
            when {
                diff > lastLapDiff && diff > 0 -> "You lapped $field! +${RaceConfig.LAP_GAIN_POINTS}"
                diff > lastLapDiff -> "You got the lap back!"
                diff < 0 -> "You've been lapped by $field! -${RaceConfig.LAP_GAIN_POINTS}"
                else -> "Lap lost back to $field"
            }
        )
        lastLapDiff = diff
    }

    private fun checkLapAnnouncements() {
        val lapsDone = (you.dist / lap).toInt()
        if (lapsDone <= lastAnnouncedLap || you.finish != null) return
        lastAnnouncedLap = lapsDone
        val toGo = config.effectiveLaps - lapsDone
        val nextLine = sprintLines.getOrNull(nextSprintToOpen)
        val lapsToSprint = nextLine?.let { ((it - lapsDone * lap) / lap).toInt() }
        val nextElim = elimLines.getOrNull(nextElimination)
        when {
            toGo == 1 -> announce("BELL - final lap!")
            toGo <= 0 -> Unit
            nextElim != null && ((nextElim - lapsDone * lap) / lap).toInt() == 1 -> announce("Bell - elimination next lap!")
            mode == RaceMode.TEMPO && lapsToSprint == 1 && sprints.isEmpty() -> announce("Points every lap from now!")
            (mode == RaceMode.POINTS_RACE || mode == RaceMode.MADISON) && lapsToSprint == 1 ->
                announce("Bell - sprint next lap!")
            else -> announce("$toGo laps to go")
        }
    }

    // ------------------------------------------------------------------ finishing

    private fun checkCatch() {
        val caught = when {
            you.dist >= firstGhost.dist -> Competitor.RIDER
            firstGhost.dist - you.dist >= lap -> Competitor.GHOST
            else -> return
        }
        caughtBy = caught
        if (caught == Competitor.RIDER) you.finish = elapsed else firstGhost.finish = elapsed
        announce(if (caught == Competitor.RIDER) "You caught the ghost! You win!" else "The ghost caught you")
        endRace()
    }

    private fun checkFinish(h: Double) {
        for (r in everyone) {
            if (r.finish != null || !r.active) continue
            val line = if (r.isYou) raceDistance else ghostFinishLine
            if (r.dist >= line) r.finish = elapsed - h + h * crossFraction(r, line)
        }
        if (you.finish == null && !ghostFinishAnnounced && !mode.isPointsBased) {
            val first = ghosts.filter { it.finish != null }.minByOrNull { it.finish!! }
            if (first != null) {
                ghostFinishAnnounced = true
                announce("${if (isField) first.name else "Ghost"} has crossed the line - keep going!")
            }
        }
        if (you.finish != null) {
            endRace()
            announce(if (winnerId == 0) "You win!" else if (isField) "${racer(winnerId!!).name} wins - you're ${ordinal(placeOf(0))}" else "The ghost wins")
        }
    }

    private fun endRace() {
        phase = RacePhase.FINISHED
        // Sprints still being contested are settled in road order.
        for (s in openSprints.toList()) {
            val rest = everyone.filter { it.active && it.id !in s.order }.sortedByDescending { it.dist }
            s.order += rest.map { it.id }
            closeSprint(s)
        }
        val order = ranking()
        winnerId = order.first().id
        margin = when {
            caughtBy != null || mode == RaceMode.ELIMINATION -> null
            else -> {
                val yourTime = you.finish ?: elapsed
                val rival = if (winnerId == 0) order.getOrNull(1) else order.first()
                rival?.let { abs(estimatedFinish(it) - yourTime) }
            }
        }
    }

    private fun estimatedFinish(r: Racer): Double {
        r.finish?.let { return it }
        val line = if (r.isYou) raceDistance else ghostFinishLine
        return elapsed + (line - r.dist).coerceAtLeast(0.0) / max(r.speed, 1.0)
    }

    /** Distance covered from the rider's own start (pursuit ghosts start half a lap up). */
    private fun progress(r: Racer) = if (r.isYou) r.dist else r.dist - ghostStart

    /** Current classification, best first. */
    private fun ranking(): List<Racer> {
        caughtBy?.let { c ->
            val catcher = if (c == Competitor.RIDER) you else firstGhost
            return listOf(catcher) + everyone.filter { it !== catcher }
        }
        val byRoad = compareBy<Racer>({ it.finish ?: Double.MAX_VALUE }, { -progress(it) })
        return when {
            mode == RaceMode.ELIMINATION ->
                everyone.filter { it.active }.sortedWith(byRoad) +
                    everyone.filter { !it.active }.sortedBy { it.eliminatedPlace }
            mode.isPointsBased -> everyone.sortedWith(compareByDescending<Racer> { totalPoints(it) }.then(byRoad))
            else -> everyone.sortedWith(byRoad)
        }
    }

    private fun placeOf(id: Int) = ranking().indexOfFirst { it.id == id } + 1

    private fun racer(id: Int) = everyone[id]

    private fun announce(message: String) {
        announcement = message
        announcementAt = elapsed
    }

    // ------------------------------------------------------------------ snapshot

    private fun Racer.snapshot() = RiderSnapshot(dist, speed, power, cadence, crank, drafting, finish)

    private fun snapshot(): RaceState {
        val racingGhosts = ghosts.filter { it.active }
        val primary = if (!isField) firstGhost
        else racingGhosts.minByOrNull { abs(track.wrappedDelta(you.dist, it.dist)) } ?: firstGhost

        val order = ranking()
        val standings = order.mapIndexed { i, r ->
            Standing(r.id, r.name, i + 1, if (mode.isPointsBased) totalPoints(r) else 0, r.dist, r.finish, r.eliminated)
        }
        val topGhost = order.firstOrNull { !it.isYou } ?: firstGhost

        val (gap, gapSec) = if (!isField) {
            val g = if (mode.isPursuit) you.dist - (firstGhost.dist - ghostStart) else you.dist - firstGhost.dist
            g to (if (g >= 0) g / max(firstGhost.speed, 1.0) else g / max(you.speed, 1.0))
        } else {
            val bestOther = racingGhosts.maxByOrNull { it.dist }
            if (bestOther == null) 0.0 to 0.0
            else {
                val g = you.dist - bestOther.dist
                g to (if (g >= 0) g / max(bestOther.speed, 1.0) else g / max(you.speed, 1.0))
            }
        }

        val riderTarget = when {
            partnerActive -> 0.5 * config.riderFtpWatts
            else -> config.workout?.targetWatts(elapsed, config.riderFtpWatts)
                ?: (config.constantGhostFraction * config.riderFtpWatts)
        }
        val madison = mode == RaceMode.MADISON
        val nextLine = sprintLines.getOrNull(nextSprintToOpen) ?: elimLines.getOrNull(nextElimination)
        return RaceState(
            mode = mode,
            phase = phase,
            countdownRemaining = countdown,
            elapsedSec = elapsed,
            rider = you.snapshot(),
            ghost = primary.snapshot(),
            gapMeters = gap,
            gapSeconds = gapSec,
            raceDistanceMeters = raceDistance,
            lapLength = lap,
            totalLaps = config.effectiveLaps,
            sprints = sprints.toList(),
            nextSprintLap = nextLine?.let { (it / lap).toInt() },
            riderSprintPoints = you.sprintPoints,
            ghostSprintPoints = topGhost.sprintPoints,
            riderLapPoints = lapPoints(you),
            ghostLapPoints = lapPoints(topGhost),
            ghostTargetWatts = if (phase == RacePhase.RACING) primary.power else ghostPower(primary),
            riderTargetWatts = riderTarget,
            targetCadence = if (partnerActive) null else config.workout?.cadenceAt(elapsed),
            announcement = announcement,
            announcementAtSec = announcementAt,
            winner = winnerId?.let { if (it == 0) Competitor.RIDER else Competitor.GHOST },
            marginSeconds = margin,
            partnerActive = if (madison) partnerActive else null,
            metersToChange = if (madison) changeMeters - (you.dist % changeMeters) else null,
            partnerPowerWatts = partnerPower,
            caughtBy = caughtBy,
            ghosts = ghosts.map { GhostSnapshot(it.id, it.name, it.snapshot(), it.level, it.eliminated) },
            standings = standings,
            riderPlace = standings.first { it.isYou }.place,
            roadPosition = 1 + racingGhosts.count { it.dist > you.dist },
            fieldSize = ghostCount,
        )
    }

    private companion object {
        const val SUBSTEP = 0.02
        const val SLING_BOOST_MPS = 0.8
        const val LEVEL_RAMP_SEC = 8.0

        fun ordinal(n: Int): String {
            val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
            return "$n$suffix"
        }
    }
}
