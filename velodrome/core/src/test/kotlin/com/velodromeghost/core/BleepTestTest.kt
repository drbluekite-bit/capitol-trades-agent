package com.velodromeghost.core

import com.velodromeghost.core.race.BleepResult
import com.velodromeghost.core.race.BleepTest
import com.velodromeghost.core.race.BleepTrendDirection
import com.velodromeghost.core.race.RaceConfig
import com.velodromeghost.core.race.RaceEngine
import com.velodromeghost.core.race.RaceMode
import com.velodromeghost.core.race.RacePhase
import com.velodromeghost.core.race.RaceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BleepTestTest {
    private fun cfg() = RaceConfig(
        mode = RaceMode.BLEEP_TEST, laps = 4, bleepLevelLaps = 4,
        bleepStartWatts = 120.0, bleepStepWatts = 10.0, riderFtpWatts = 200.0,
    )

    /** Rider who always matches the ghost's power, up to a ceiling. */
    private fun ride(ceiling: Double): RaceState {
        val engine = RaceEngine(cfg())
        var s = engine.tick(3.1, 0.0, null)
        var t = 0.0
        while (s.phase != RacePhase.FINISHED && t < 20000) {
            val target = s.bleep!!.levelWatts
            s = engine.tick(0.05, minOf(target + 3, ceiling), null)
            t += 0.05
        }
        return s
    }

    @Test
    fun levelsStepUpAndTestEndsAtTheCeiling() {
        val s = ride(ceiling = 185.0)
        assertEquals(RacePhase.FINISHED, s.phase)
        val b = s.bleep!!
        // 120,130,...,180 are passable (7 levels); 190 W is beyond a 185 W ceiling.
        assertEquals(7, b.completedLevels)
        assertEquals(180.0, b.lastPassedWatts, 1e-9)
        assertEquals(190.0, b.levelWatts, 1e-9)
        assertNotNull(b.failedByMeters)
        assertTrue(b.failedByMeters!! > 1.0)
        assertTrue("no drafting in the test", !s.rider.drafting)
    }

    @Test
    fun aBankedLeadDoesNotCarryOver() {
        val engine = RaceEngine(cfg())
        var s = engine.tick(3.1, 0.0, null)
        // Smash level 1 to build a big lead.
        while (s.bleep!!.completedLevels == 0) s = engine.tick(0.05, 400.0, null)
        assertTrue("re-synced", kotlin.math.abs(s.gapMeters) < 1.5)
    }

    private fun result(day: Int, level: Int) =
        BleepResult(day * 86_400_000L, level, 120.0 + (level - 1) * 10, 200, 75, 120.0, 10.0, 4)

    @Test
    fun trends() {
        val a = result(1, 7)
        assertEquals(BleepTrendDirection.FIRST, BleepTest.trend(listOf(a), a).direction)
        val b = result(30, 9)
        val t = BleepTest.trend(listOf(a, b), b)
        assertEquals(BleepTrendDirection.IMPROVING, t.direction)
        assertEquals(2, t.levelChange)
        assertEquals("+2 levels (+20 W)", BleepTest.describeChange(t))
        val c = result(60, 9)
        assertEquals(BleepTrendDirection.PLATEAU, BleepTest.trend(listOf(a, b, c), c).direction)
        val d = result(90, 8)
        assertEquals(BleepTrendDirection.REGRESSING, BleepTest.trend(listOf(a, b, c, d), d).direction)
        // Different protocol isn't compared.
        val other = BleepResult(100 * 86_400_000L, 3, 200.0, 200, 75, 150.0, 25.0, 4)
        assertEquals(BleepTrendDirection.FIRST, BleepTest.trend(listOf(a, b, c, d, other), other).direction)
    }
}
