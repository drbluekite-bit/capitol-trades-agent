package com.velodromeghost.core.fit

import java.io.ByteArrayOutputStream
import kotlin.math.roundToLong

/**
 * Minimal encoder for Garmin FIT activity files, readable by Strava, Garmin Connect,
 * TrainingPeaks, intervals.icu and others.
 *
 * Writes: file_id, timer start event, one record per second, timer stop event, one lap,
 * one session and the activity message. The summary (avg cadence, avg/max power, total work,
 * distance, calories) goes in both the lap and the session.
 */
object FitWriter {
    /** Seconds between the Unix epoch and the FIT epoch (1989-12-31T00:00:00Z). */
    const val FIT_EPOCH_OFFSET = 631_065_600L

    private const val ENUM = 0x00
    private const val UINT8 = 0x02
    private const val UINT16 = 0x84
    private const val UINT32 = 0x86
    private const val UINT32Z = 0x8C

    private const val INVALID_U8 = 0xFFL
    private const val INVALID_U16 = 0xFFFFL

    // Global message numbers
    private const val MESG_FILE_ID = 0
    private const val MESG_SESSION = 18
    private const val MESG_LAP = 19
    private const val MESG_RECORD = 20
    private const val MESG_EVENT = 21
    private const val MESG_ACTIVITY = 34

    // Enum values
    private const val SPORT_CYCLING = 2L
    private const val SUB_SPORT_INDOOR_CYCLING = 6L
    private const val EVENT_TIMER = 0L
    private const val EVENT_SESSION = 8L
    private const val EVENT_LAP = 9L
    private const val EVENT_ACTIVITY = 26L
    private const val EVENT_TYPE_START = 0L
    private const val EVENT_TYPE_STOP = 1L
    private const val EVENT_TYPE_STOP_ALL = 4L

    private class Field(val num: Int, val size: Int, val baseType: Int)

    private class Message(val local: Int, val global: Int, val fields: List<Field>)

    private val fileIdMsg = Message(
        0, MESG_FILE_ID, listOf(
            Field(0, 1, ENUM),       // type
            Field(1, 2, UINT16),     // manufacturer
            Field(2, 2, UINT16),     // product
            Field(3, 4, UINT32Z),    // serial_number
            Field(4, 4, UINT32),     // time_created
        )
    )

    private val eventMsg = Message(
        1, MESG_EVENT, listOf(
            Field(253, 4, UINT32),   // timestamp
            Field(0, 1, ENUM),       // event
            Field(1, 1, ENUM),       // event_type
        )
    )

    private val recordMsg = Message(
        2, MESG_RECORD, listOf(
            Field(253, 4, UINT32),   // timestamp
            Field(7, 2, UINT16),     // power (W)
            Field(4, 1, UINT8),      // cadence (rpm)
            Field(3, 1, UINT8),      // heart_rate (bpm)
            Field(6, 2, UINT16),     // speed (m/s * 1000)
            Field(5, 4, UINT32),     // distance (m * 100)
        )
    )

    private val lapMsg = Message(
        3, MESG_LAP, listOf(
            Field(254, 2, UINT16),   // message_index
            Field(253, 4, UINT32),   // timestamp
            Field(0, 1, ENUM),       // event
            Field(1, 1, ENUM),       // event_type
            Field(2, 4, UINT32),     // start_time
            Field(7, 4, UINT32),     // total_elapsed_time (s * 1000)
            Field(8, 4, UINT32),     // total_timer_time (s * 1000)
            Field(9, 4, UINT32),     // total_distance (m * 100)
            Field(11, 2, UINT16),    // total_calories (kcal)
            Field(13, 2, UINT16),    // avg_speed
            Field(14, 2, UINT16),    // max_speed
            Field(15, 1, UINT8),     // avg_heart_rate
            Field(16, 1, UINT8),     // max_heart_rate
            Field(17, 1, UINT8),     // avg_cadence
            Field(18, 1, UINT8),     // max_cadence
            Field(19, 2, UINT16),    // avg_power
            Field(20, 2, UINT16),    // max_power
            Field(25, 1, ENUM),      // sport
            Field(39, 1, ENUM),      // sub_sport
            Field(41, 4, UINT32),    // total_work (J)
        )
    )

    private val sessionMsg = Message(
        4, MESG_SESSION, listOf(
            Field(254, 2, UINT16),   // message_index
            Field(253, 4, UINT32),   // timestamp
            Field(0, 1, ENUM),       // event
            Field(1, 1, ENUM),       // event_type
            Field(2, 4, UINT32),     // start_time
            Field(5, 1, ENUM),       // sport
            Field(6, 1, ENUM),       // sub_sport
            Field(7, 4, UINT32),     // total_elapsed_time
            Field(8, 4, UINT32),     // total_timer_time
            Field(9, 4, UINT32),     // total_distance
            Field(11, 2, UINT16),    // total_calories
            Field(14, 2, UINT16),    // avg_speed
            Field(15, 2, UINT16),    // max_speed
            Field(16, 1, UINT8),     // avg_heart_rate
            Field(17, 1, UINT8),     // max_heart_rate
            Field(18, 1, UINT8),     // avg_cadence
            Field(19, 1, UINT8),     // max_cadence
            Field(20, 2, UINT16),    // avg_power
            Field(21, 2, UINT16),    // max_power
            Field(25, 2, UINT16),    // first_lap_index
            Field(26, 2, UINT16),    // num_laps
            Field(28, 1, ENUM),      // trigger (activity_end)
            Field(48, 4, UINT32),    // total_work (J)
        )
    )

    private val activityMsg = Message(
        5, MESG_ACTIVITY, listOf(
            Field(253, 4, UINT32),   // timestamp
            Field(0, 4, UINT32),     // total_timer_time
            Field(1, 2, UINT16),     // num_sessions
            Field(2, 1, ENUM),       // type (manual)
            Field(3, 1, ENUM),       // event
            Field(4, 1, ENUM),       // event_type
            Field(5, 4, UINT32),     // local_timestamp
        )
    )

    /**
     * @param startUnixSec wall-clock start of the race (Unix seconds, UTC)
     * @param utcOffsetSec local time zone offset, for the activity's local timestamp
     */
    fun encode(
        startUnixSec: Long,
        samples: List<RideSample>,
        summary: RideSummary,
        utcOffsetSec: Int = 0,
    ): ByteArray {
        val body = ByteArrayOutputStream()
        val start = startUnixSec - FIT_EPOCH_OFFSET
        val durationS = summary.durationSec
        val end = start + durationS.roundToLong()

        body.define(fileIdMsg)
        body.data(fileIdMsg, 4L, 255L, 1L, 0x56474853L, start) // activity, development manufacturer

        body.define(eventMsg)
        body.data(eventMsg, start, EVENT_TIMER, EVENT_TYPE_START)

        body.define(recordMsg)
        for (s in samples) {
            body.data(
                recordMsg,
                start + s.elapsedSec,
                s.powerWatts.toLong().coerceIn(0, 65534),
                s.cadenceRpm?.toLong()?.coerceIn(0, 254) ?: INVALID_U8,
                s.heartRate?.toLong()?.coerceIn(0, 254) ?: INVALID_U8,
                speed(s.speedMps),
                (s.distanceM * 100).roundToLong().coerceAtLeast(0),
            )
        }

        body.data(eventMsg, end, EVENT_TIMER, EVENT_TYPE_STOP_ALL)

        val elapsedMs = (durationS * 1000).roundToLong()
        val distance = (summary.distanceM * 100).roundToLong()
        val kcal = summary.kcal.roundToLong().coerceIn(0, 65534)
        val avgHr = summary.avgHeartRate?.toLong() ?: INVALID_U8
        val maxHr = summary.maxHeartRate?.toLong() ?: INVALID_U8
        val avgCad = summary.avgCadenceRpm?.roundToLong()?.coerceIn(0, 254) ?: INVALID_U8
        val maxCad = summary.maxCadenceRpm?.toLong()?.coerceIn(0, 254) ?: INVALID_U8
        val avgPower = summary.avgPowerWatts.roundToLong().coerceIn(0, 65534)
        val maxPower = summary.maxPowerWatts.toLong().coerceIn(0, 65534)
        val work = summary.totalWorkJoules.roundToLong().coerceAtLeast(0)

        body.define(lapMsg)
        body.data(
            lapMsg, 0L, end, EVENT_LAP, EVENT_TYPE_STOP, start, elapsedMs, elapsedMs, distance, kcal,
            speed(summary.avgSpeedMps), speed(summary.maxSpeedMps), avgHr, maxHr, avgCad, maxCad,
            avgPower, maxPower, SPORT_CYCLING, SUB_SPORT_INDOOR_CYCLING, work,
        )

        body.define(sessionMsg)
        body.data(
            sessionMsg, 0L, end, EVENT_SESSION, EVENT_TYPE_STOP, start, SPORT_CYCLING, SUB_SPORT_INDOOR_CYCLING,
            elapsedMs, elapsedMs, distance, kcal, speed(summary.avgSpeedMps), speed(summary.maxSpeedMps),
            avgHr, maxHr, avgCad, maxCad, avgPower, maxPower, 0L, 1L, 0L, work,
        )

        body.define(activityMsg)
        body.data(activityMsg, end, elapsedMs, 1L, 0L, EVENT_ACTIVITY, EVENT_TYPE_STOP, end + utcOffsetSec)

        val data = body.toByteArray()
        val out = ByteArrayOutputStream(data.size + 16)
        val header = ByteArrayOutputStream()
        header.write(14)                 // header size
        header.write(0x20)               // protocol version 2.0
        header.le(2100, 2)               // profile version 21.00
        header.le(data.size.toLong(), 4) // data size
        header.write(".FIT".toByteArray(Charsets.US_ASCII))
        val headerBytes = header.toByteArray()
        out.write(headerBytes)
        out.le(crc(headerBytes).toLong(), 2)
        out.write(data)
        val soFar = out.toByteArray()
        out.le(crc(soFar).toLong(), 2)
        return out.toByteArray()
    }

    private fun speed(mps: Double): Long = (mps * 1000).roundToLong().coerceIn(0, 65534)

    private fun ByteArrayOutputStream.define(m: Message) {
        write(0x40 or m.local)
        write(0)          // reserved
        write(0)          // little endian
        le(m.global.toLong(), 2)
        write(m.fields.size)
        for (f in m.fields) {
            write(f.num)
            write(f.size)
            write(f.baseType)
        }
    }

    private fun ByteArrayOutputStream.data(m: Message, vararg values: Long) {
        require(values.size == m.fields.size) { "Message ${m.global}: expected ${m.fields.size} values" }
        write(m.local)
        m.fields.forEachIndexed { i, f -> le(values[i], f.size) }
    }

    private fun ByteArrayOutputStream.le(value: Long, bytes: Int) {
        for (i in 0 until bytes) write(((value shr (8 * i)) and 0xFF).toInt())
    }

    private val CRC_TABLE = intArrayOf(
        0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
        0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    )

    /** FIT's CRC-16. */
    fun crc(bytes: ByteArray, initial: Int = 0): Int {
        var crc = initial
        for (b in bytes) {
            val byte = b.toInt() and 0xFF
            var tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[byte and 0xF]
            tmp = CRC_TABLE[crc and 0xF]
            crc = (crc shr 4) and 0x0FFF
            crc = crc xor tmp xor CRC_TABLE[(byte shr 4) and 0xF]
        }
        return crc
    }
}
