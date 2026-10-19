package com.velodromeghost.core

import com.velodromeghost.core.race.Competitor
import com.velodromeghost.core.race.Omnium
import com.velodromeghost.core.race.RaceConfig
import com.velodromeghost.core.race.RaceEngine
import com.velodromeghost.core.race.RaceMode
import com.velodromeghost.core.race.RacePhase
import com.velodromeghost.core.race.RaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RaceFormatsTest {
    private fun run(engine: RaceEngine, power: Double, each: (RaceState) -> Unit = {}): RaceState {
        var s = engine.tick(0.0, power, null)
        var t = 0.0
        while (s.phase != RacePhase.FINISHED && t < 7200) {
            s = engine.tick(0.05, power, null)
            each(s)
            t += 0.05
        }
        return s
    }

    @Test
    fun pursuitStartsHalfALapApartAndCatchWins() {
        val cfg = RaceConfig(mode = RaceMode.INDIVIDUAL_PURSUIT, laps = 16, riderFtpWatts = 200.0, constantGhostFraction = 0.6)
        val engine = RaceEngine(cfg)
        val start = engine.tick(0.0, 0.0, null)
        assertEquals(125.0, start.ghost.distance, 1e-9)
        assertEquals(0.0, start.gapMeters, 1e-9)
        val s = run(engine, 450.0)
        assertEquals(Competitor.RIDER, s.caughtBy)
        assertEquals(Competitor.RIDER, s.winner)
        assertNull(s.marginSeconds)
    }

    @Test
    fun pursuitDecidedOnTimeWithoutCatch() {
        val cfg = RaceConfig(mode = RaceMode.INDIVIDUAL_PURSUIT, laps = 12, riderFtpWatts = 200.0)
        var maxDraft = false
        val s = run(RaceEngine(cfg), 175.0) { maxDraft = maxDraft || it.rider.drafting }
        assertNull(s.caughtBy)
        assertEquals(Competitor.RIDER, s.winner)
        assertNotNull(s.marginSeconds)
        assertTrue("no drafting in a pursuit", !maxDraft)
    }

    @Test
    fun madisonAlternatesTurns() {
        val cfg = RaceConfig(mode = RaceMode.MADISON, laps = 20, sprintEveryLaps = 10, madisonChangeLaps = 2.0, riderFtpWatts = 200.0)
        val turns = mutableListOf<Boolean>()
        // You soft-pedal; your partner still races, so the team keeps moving.
        val s = run(RaceEngine(cfg), 60.0) { st -> st.partnerActive?.let { if (turns.lastOrNull() != it) turns += it } }
        assertEquals(RacePhase.FINISHED, s.phase)
        assertTrue("turns: $turns", turns.size >= 9)
        assertEquals(false, turns.first())
        assertEquals(2, s.sprints.size)
    }

    @Test
    fun tempoAwardsOnePointPerLapFromLapFive() {
        val cfg = RaceConfig(mode = RaceMode.TEMPO, laps = 10, riderFtpWatts = 200.0)
        val s = run(RaceEngine(cfg), 400.0)
        assertEquals(6, s.sprints.size) // laps 5..10
        assertTrue(s.sprints.all { it.riderPoints + it.ghostPoints == 1 })
        assertEquals(Competitor.RIDER, s.winner)
    }

    @Test
    fun omniumScoring() {
        fun ev(mode: RaceMode, placings: List<Int>, points: Map<Int, Int>) =
            Omnium.EventResult(mode, true, placings, points, emptyMap())
        val results = listOf(
            ev(RaceMode.SCRATCH, listOf(0, 1), mapOf(0 to 40, 1 to 38)),
            ev(RaceMode.TEMPO, listOf(1, 0), mapOf(0 to 38, 1 to 40)),
            ev(RaceMode.ELIMINATION, listOf(1, 0), mapOf(0 to 38, 1 to 40)),
            ev(RaceMode.POINTS_RACE, listOf(0, 1), mapOf(0 to 15, 1 to 11)),
        )
        assertEquals(131, Omnium.riderTotal(results))
        assertEquals(129, Omnium.ghostTotal(results))
        assertEquals(Competitor.RIDER, Omnium.overallWinner(results))
        assertEquals(40, Omnium.placePoints(1))
        assertEquals(36, Omnium.placePoints(3))

        val pr = run(RaceEngine(RaceConfig(mode = RaceMode.POINTS_RACE, laps = 8, sprintEveryLaps = 4)), 380.0)
        val scored = Omnium.score(pr)
        assertEquals(pr.riderTotalPoints, scored.riderPoints)
        assertEquals(1, scored.riderPlace)
    }

    @Test
    fun fieldLevelsWanderWithinRange() {
        val cfg = RaceConfig(mode = RaceMode.POINTS_RACE, laps = 40, fieldSize = 6, seed = 42, riderFtpWatts = 200.0)
        val engine = RaceEngine(cfg)
        val seen = HashMap<Int, MutableSet<Long>>()
        val s = run(engine, 200.0) { st ->
            assertEquals(6, st.ghosts.size)
            for (g in st.ghosts) {
                assertTrue("level ${g.level}", g.level in 0.6 - 1e-9..1.25 + 1e-9)
                seen.getOrPut(g.id) { mutableSetOf() } += Math.round(g.level * 100)
            }
        }
        assertEquals(RacePhase.FINISHED, s.phase)
        assertTrue("levels should change during the race", seen.values.all { it.size > 3 })
        assertEquals(7, s.standings.size)
        // Up to four riders score in each sprint.
        assertTrue(s.sprints.all { it.placings.size == 4 })
        assertEquals(s.standings.map { it.place }, (1..7).toList())
        // You're in the classification and sprint winners are named.
        assertTrue(s.standings.any { it.isYou })
        assertTrue(s.sprints.all { it.winnerName.isNotEmpty() })
    }

    @Test
    fun eliminationKnocksOutOneRiderEveryTwoLaps() {
        val cfg = RaceConfig(mode = RaceMode.ELIMINATION, fieldSize = 5, seed = 7, riderFtpWatts = 200.0)
        assertEquals(10, cfg.effectiveLaps)
        // Strong rider survives to the end.
        val win = run(RaceEngine(cfg), 600.0)
        assertEquals(Competitor.RIDER, win.winner)
        assertEquals(5, win.ghosts.count { it.eliminated })
        // Very weak rider is the first out.
        val out = run(RaceEngine(cfg), 40.0)
        assertEquals(RacePhase.FINISHED, out.phase)
        assertEquals(6, out.riderPlace)
        assertTrue(out.standings.first { it.isYou }.eliminated)
    }

    @Test
    fun singleGhostModesIgnoreFieldSize() {
        val cfg = RaceConfig(mode = RaceMode.INDIVIDUAL_PURSUIT, laps = 12, fieldSize = 6)
        assertEquals(1, cfg.ghostCount)
        val s = RaceEngine(cfg).tick(0.0, 0.0, null)
        assertEquals(1, s.ghosts.size)
    }
}
