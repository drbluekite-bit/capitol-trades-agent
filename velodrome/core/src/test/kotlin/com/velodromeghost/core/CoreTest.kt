package com.velodromeghost.core

import com.velodromeghost.core.ble.CyclingPowerParser
import com.velodromeghost.core.ble.FtmsCommands
import com.velodromeghost.core.ble.IndoorBikeDataParser
import com.velodromeghost.core.physics.Physics
import com.velodromeghost.core.physics.RiderParams
import com.velodromeghost.core.race.Competitor
import com.velodromeghost.core.race.RaceConfig
import com.velodromeghost.core.race.RaceEngine
import com.velodromeghost.core.race.RaceMode
import com.velodromeghost.core.race.RacePhase
import com.velodromeghost.core.race.RaceState
import com.velodromeghost.core.track.Track
import com.velodromeghost.core.workout.SegmentKind
import com.velodromeghost.core.workout.ZwoParseException
import com.velodromeghost.core.workout.ZwoParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ZwoParserTest {
    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <workout_file>
          <author>Test</author>
          <name>Sample</name>
          <description>desc</description>
          <sportType>bike</sportType>
          <workout>
            <Warmup Duration="60" PowerLow="0.5" PowerHigh="0.8" Cadence="85"/>
            <SteadyState Duration="30" Power="0.9">
              <textevent timeoffset="10" message="Hold it"/>
            </SteadyState>
            <IntervalsT Repeat="2" OnDuration="10" OffDuration="20" OnPower="1.5" OffPower="0.6" Cadence="110" CadenceResting="85"/>
            <FreeRide Duration="10"/>
            <MaxEffort Duration="5"/>
            <cooldown duration="20" powerlow="0.4" powerhigh="0.7"/>
            <Unknown Duration="99"/>
          </workout>
        </workout_file>
    """.trimIndent()

    @Test
    fun parsesAllBlocks() {
        val w = ZwoParser.parse(sample)
        assertEquals("Sample", w.name)
        assertEquals(60 + 30 + 2 * 30 + 10 + 5 + 20, w.durationSec.toInt())
        assertEquals(9, w.segments.size)
        assertEquals(SegmentKind.INTERVAL_ON, w.segments[2].kind)
        assertEquals(110, w.segments[2].cadence)
        assertEquals(85, w.segments[3].cadence)
        assertEquals(0.5, w.powerFractionAt(0.0), 1e-9)
        assertEquals(0.65, w.powerFractionAt(30.0), 1e-9) // halfway through the warmup
        assertEquals(0.9, w.powerFractionAt(75.0), 1e-9)
        assertEquals(1.5, w.powerFractionAt(95.0), 1e-9)
        // Cooldown goes down even though PowerLow < PowerHigh in the file.
        val cd = w.segments.last()
        assertEquals(0.7, cd.powerStart, 1e-9)
        assertEquals(0.4, cd.powerEnd, 1e-9)
        assertEquals(0.4, w.powerFractionAt(10_000.0), 1e-9)
        assertEquals(1, w.textEvents.size)
        assertEquals(70.0, w.textEvents[0].timeSec, 1e-9)
    }

    @Test
    fun rejectsGarbage() {
        try {
            ZwoParser.parse("<html><body/></html>")
            fail("expected exception")
        } catch (_: ZwoParseException) {
        }
    }

    @Test
    fun bundledWorkoutsParse() {
        val dir = listOf(File("../app/src/main/assets/workouts"), File("app/src/main/assets/workouts"))
            .firstOrNull { it.isDirectory } ?: return
        val files = dir.listFiles { f -> f.extension == "zwo" }.orEmpty()
        assertTrue(files.isNotEmpty())
        files.forEach { f -> f.inputStream().use { assertTrue(ZwoParser.parse(it).durationSec > 0) } }
    }
}

class PhysicsTest {
    @Test
    fun plausibleTrackSpeeds() {
        val p = RiderParams(83.0)
        val v250 = Physics.steadyStateSpeed(250.0, p) * 3.6
        assertTrue("250 W gave $v250 km/h", v250 in 40.0..47.0)
        val drafting = Physics.steadyStateSpeed(250.0, p, Physics.draftFactor(0.8)) * 3.6
        assertTrue(drafting > v250 + 3)
    }

    @Test
    fun accelerationIntegratesToSteadyState() {
        val p = RiderParams(83.0)
        var v = 0.0
        repeat(120 * 50) { v = Physics.step(v, 250.0, p, 1.0, 0.02) }
        assertEquals(Physics.steadyStateSpeed(250.0, p), v, 0.05)
    }
}

class TrackTest {
    @Test
    fun geometryIsContinuous() {
        val t = Track()
        var prev = t.pointAtU(0.0, 0.0)
        var u = 0.5
        while (u <= t.lapLength) {
            val p = t.pointAtU(u, 0.0)
            val d = Math.hypot(p.x - prev.x, p.y - prev.y)
            assertTrue("jump of $d at u=$u", d < 0.6)
            prev = p
            u += 0.5
        }
        assertEquals(0.0, t.bendness(0.0), 1e-9) // finish line is on the straight
        assertEquals(1.0, t.bendness(t.lapLength - t.finishLineU + t.straightLength + t.bendLength / 2), 1e-9)
    }
}

class RaceEngineTest {
    private fun run(engine: RaceEngine, power: Double, maxSec: Double = 3600.0): RaceState {
        var s = engine.tick(0.0, power, null)
        var t = 0.0
        while (s.phase != RacePhase.FINISHED && t < maxSec) {
            s = engine.tick(0.05, power, null)
            t += 0.05
        }
        return s
    }

    @Test
    fun strongerRiderWins1v1() {
        val cfg = RaceConfig(mode = RaceMode.SPRINT_1V1, laps = 4, riderFtpWatts = 200.0, ghostAttacksSprints = false)
        val s = run(RaceEngine(cfg), 260.0)
        assertEquals(RacePhase.FINISHED, s.phase)
        assertEquals(Competitor.RIDER, s.winner)
        assertNotNull(s.rider.finishTimeSec)
        assertTrue(s.marginSeconds!! > 0)
    }

    @Test
    fun weakerRiderLoses1v1() {
        val cfg = RaceConfig(mode = RaceMode.SPRINT_1V1, laps = 4, riderFtpWatts = 200.0)
        val s = run(RaceEngine(cfg), 120.0)
        assertEquals(Competitor.GHOST, s.winner)
        assertNotNull(s.ghost.finishTimeSec)
    }

    @Test
    fun omniumAwardsSprintPoints() {
        val cfg = RaceConfig(mode = RaceMode.OMNIUM_POINTS, laps = 12, sprintEveryLaps = 4, riderFtpWatts = 200.0)
        val s = run(RaceEngine(cfg), 400.0)
        assertEquals(3, s.sprints.size)
        assertTrue(s.sprints.last().isFinal)
        assertTrue(s.sprints.all { it.winner == Competitor.RIDER })
        // 5 + 5 + 10, plus lap gains at 400 W vs 170 W.
        assertEquals(20, s.riderSprintPoints)
        assertEquals(3 + 3 + 6, s.ghostSprintPoints)
        assertTrue(s.riderLapPoints >= 20)
        assertEquals(Competitor.RIDER, s.winner)
    }

    @Test
    fun draftingHelpsFollower() {
        // Rider at the same power as the ghost, starting right on its wheel, ends up ahead.
        val cfg = RaceConfig(laps = 2, riderFtpWatts = 200.0, ghostHeadStartMeters = 1.0, constantGhostFraction = 1.0, ghostAttacksSprints = false)
        val engine = RaceEngine(cfg)
        engine.tick(3.1, 200.0, null)
        var s = engine.tick(0.05, 200.0, null)
        repeat(200) { s = engine.tick(0.05, 200.0, null) }
        assertTrue(s.rider.drafting || s.gapMeters > -1.0)
        assertTrue(s.rider.speedMps >= s.ghost.speedMps - 0.01)
    }
}

class FtmsTest {
    @Test
    fun parsesIndoorBikeData() {
        // flags: speed present, cadence (0x04), power (0x40) => 0x0044
        val data = byteArrayOf(0x44, 0x00, 0x10, 0x0E, 0xB4.toByte(), 0x00, 0xFA.toByte(), 0x00)
        val s = IndoorBikeDataParser.parse(data)
        assertEquals(36.0, s.speedKmh!!, 1e-9)
        assertEquals(90.0, s.cadenceRpm!!, 1e-9)
        assertEquals(250, s.powerWatts)
    }

    @Test
    fun parsesCyclingPowerCadence() {
        val p = CyclingPowerParser()
        fun pkt(revs: Int, time: Int) = byteArrayOf(
            0x20, 0x00, 0xC8.toByte(), 0x00,
            (revs and 0xFF).toByte(), (revs shr 8).toByte(), (time and 0xFF).toByte(), (time shr 8).toByte(),
        )
        p.parse(pkt(10, 1000), 0)
        val s = p.parse(pkt(11, 1000 + 683), 700) // one rev in 0.667 s = 90 rpm
        assertEquals(200, s.powerWatts)
        assertEquals(90.0, s.cadenceRpm!!, 0.5)
    }

    @Test
    fun encodesCommands() {
        assertEquals(listOf(0x05, 0x2C, 0x01), FtmsCommands.setTargetPower(300).map { it.toInt() and 0xFF })
        val sim = FtmsCommands.setSimulation(0.0, 1.5, 0.0025, 0.14).map { it.toInt() and 0xFF }
        assertEquals(listOf(0x11, 0, 0, 150, 0, 25, 14), sim)
    }
}
