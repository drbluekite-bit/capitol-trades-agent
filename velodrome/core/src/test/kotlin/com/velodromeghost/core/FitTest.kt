package com.velodromeghost.core

import com.velodromeghost.core.fit.FitWriter
import com.velodromeghost.core.fit.RideRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FitTest {
    /** Tiny FIT reader: global message number -> list of {field number -> value}. */
    private fun decode(bytes: ByteArray): Map<Int, List<Map<Int, Long>>> {
        fun u(i: Int) = bytes[i].toLong() and 0xFF
        fun le(pos: Int, n: Int): Long {
            var v = 0L
            for (k in 0 until n) v = v or (u(pos + k) shl (8 * k))
            return v
        }
        val headerSize = u(0).toInt()
        val dataSize = le(4, 4).toInt()
        assertEquals(".FIT", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(headerSize + dataSize + 2, bytes.size)

        val defs = HashMap<Int, Pair<Int, List<Pair<Int, Int>>>>()
        val out = HashMap<Int, MutableList<Map<Int, Long>>>()
        var p = headerSize
        val end = headerSize + dataSize
        while (p < end) {
            val h = u(p++).toInt()
            assertEquals("compressed headers not expected", 0, h and 0x80)
            val local = h and 0x0F
            if (h and 0x40 != 0) {
                p += 2 // reserved, architecture
                val global = le(p, 2).toInt(); p += 2
                val n = u(p++).toInt()
                val fields = (0 until n).map { val f = u(p).toInt() to u(p + 1).toInt(); p += 3; f }
                defs[local] = global to fields
            } else {
                val (global, fields) = defs.getValue(local)
                val values = HashMap<Int, Long>()
                for ((num, size) in fields) { values[num] = le(p, size); p += size }
                out.getOrPut(global) { mutableListOf() } += values
            }
        }
        return out
    }

    @Test
    fun encodesValidActivity() {
        val rec = RideRecorder()
        for (i in 0..1200) {
            val t = i * 0.05
            val power = if (t < 30) 200.0 else 300.0
            rec.update(t, power, 90.0, 140, 12.0, 12.0 * t)
        }
        val summary = rec.summary()
        assertEquals(250.0, summary.avgPowerWatts, 2.0)
        assertEquals(15.0, summary.totalWorkKj, 0.2)
        assertEquals(15.0 / 4.184 / 0.24, summary.kcal, 0.5)
        assertEquals(90.0, summary.avgCadenceRpm!!, 1e-9)
        assertEquals(61, rec.samples.size)

        val start = 1_760_000_000L
        val bytes = FitWriter.encode(start, rec.samples, summary, utcOffsetSec = 3600)

        assertEquals("file CRC", 0, FitWriter.crc(bytes))
        assertEquals("header CRC", 0, FitWriter.crc(bytes.copyOfRange(0, 14)))

        val msgs = decode(bytes)
        val fitStart = start - FitWriter.FIT_EPOCH_OFFSET
        assertEquals(4L, msgs.getValue(0).single()[0])
        val records = msgs.getValue(20)
        assertEquals(61, records.size)
        assertEquals(fitStart, records.first()[253])
        assertEquals(300L, records.last()[7])
        assertEquals(90L, records.last()[4])

        val session = msgs.getValue(18).single()
        assertEquals(250L, session[20]!!.toDouble(), 2.0)
        assertEquals(90L, session[18])
        assertEquals(2L, session[5])
        assertEquals(summary.totalWorkJoules, session[48]!!.toDouble(), 1.0)
        assertEquals(Math.round(summary.kcal), session[11])
        assertEquals(Math.round(summary.distanceM * 100), session[9])
        assertEquals(1, msgs.getValue(19).size)
        assertEquals(1L, msgs.getValue(34).single()[1])
        assertTrue(msgs.getValue(21).size == 2)
    }

    private fun assertEquals(expected: Long, actual: Double, delta: Double) =
        assertEquals(expected.toDouble(), actual, delta)
}
