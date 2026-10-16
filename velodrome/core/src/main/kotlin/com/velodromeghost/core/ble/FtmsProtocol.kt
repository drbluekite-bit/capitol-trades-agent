package com.velodromeghost.core.ble

import java.util.UUID
import kotlin.math.roundToInt

/**
 * Bluetooth GATT protocol pieces for SmartSpin2k (and any other FTMS / Cycling Power device).
 *
 * SmartSpin2k reads the Peloton's power/cadence over its serial cable and re-broadcasts them as a
 * standard Fitness Machine Service (FTMS) trainer, which also accepts ERG and simulation commands.
 */
object GattUuids {
    fun short(id: Int): UUID = UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", id))

    val FTMS_SERVICE: UUID = short(0x1826)
    val INDOOR_BIKE_DATA: UUID = short(0x2AD2)
    val FTMS_CONTROL_POINT: UUID = short(0x2AD9)
    val FTMS_STATUS: UUID = short(0x2ADA)

    val CYCLING_POWER_SERVICE: UUID = short(0x1818)
    val CYCLING_POWER_MEASUREMENT: UUID = short(0x2A63)

    val HEART_RATE_SERVICE: UUID = short(0x180D)
    val HEART_RATE_MEASUREMENT: UUID = short(0x2A37)

    val CCCD: UUID = short(0x2902)
}

data class BikeSample(
    val powerWatts: Int? = null,
    val cadenceRpm: Double? = null,
    val speedKmh: Double? = null,
    val heartRate: Int? = null,
) {
    /** Fields present in [newer] replace ours (FTMS devices may split data across packets). */
    fun merge(newer: BikeSample) = BikeSample(
        powerWatts = newer.powerWatts ?: powerWatts,
        cadenceRpm = newer.cadenceRpm ?: cadenceRpm,
        speedKmh = newer.speedKmh ?: speedKmh,
        heartRate = newer.heartRate ?: heartRate,
    )
}

private class ByteReader(private val data: ByteArray) {
    var pos = 0
        private set

    fun has(n: Int) = pos + n <= data.size
    private fun b(i: Int) = data[i].toInt() and 0xFF

    fun u8(): Int { need(1); return b(pos).also { pos += 1 } }
    fun u16(): Int { need(2); return (b(pos) or (b(pos + 1) shl 8)).also { pos += 2 } }
    fun s16(): Int = u16().let { if (it >= 0x8000) it - 0x10000 else it }
    fun skip(n: Int) { need(n); pos += n }

    private fun need(n: Int) {
        if (!has(n)) throw TruncatedPacket()
    }
}

private class TruncatedPacket : RuntimeException()

/** FTMS Indoor Bike Data (0x2AD2). */
object IndoorBikeDataParser {
    fun parse(data: ByteArray): BikeSample {
        val r = ByteReader(data)
        var speed: Double? = null
        var cadence: Double? = null
        var power: Int? = null
        var hr: Int? = null
        try {
            val flags = r.u16()
            // Bit 0 is "More Data": when CLEAR, instantaneous speed is present.
            if (flags and 0x0001 == 0) speed = r.u16() / 100.0
            if (flags and 0x0002 != 0) r.skip(2) // average speed
            if (flags and 0x0004 != 0) cadence = r.u16() / 2.0
            if (flags and 0x0008 != 0) r.skip(2) // average cadence
            if (flags and 0x0010 != 0) r.skip(3) // total distance
            if (flags and 0x0020 != 0) r.skip(2) // resistance level
            if (flags and 0x0040 != 0) power = r.s16()
            if (flags and 0x0080 != 0) r.skip(2) // average power
            if (flags and 0x0100 != 0) r.skip(5) // energy
            if (flags and 0x0200 != 0) hr = r.u8()
        } catch (_: TruncatedPacket) {
            // keep whatever was decoded
        }
        return BikeSample(power, cadence, speed, hr?.takeIf { it > 0 })
    }
}

/** Cycling Power Measurement (0x2A63). Cadence is derived from crank revolution data, so this is stateful. */
class CyclingPowerParser {
    private var lastRevs = -1
    private var lastEventTime = -1
    private var lastCadence = 0.0
    private var lastRevChangeMs = 0L

    fun parse(data: ByteArray, nowMs: Long): BikeSample {
        val r = ByteReader(data)
        var power: Int? = null
        var cadence: Double? = null
        try {
            val flags = r.u16()
            power = r.s16()
            if (flags and 0x0001 != 0) r.skip(1) // pedal power balance
            if (flags and 0x0004 != 0) r.skip(2) // accumulated torque
            if (flags and 0x0010 != 0) r.skip(6) // wheel revolution data
            if (flags and 0x0020 != 0) {
                val revs = r.u16()
                val time = r.u16() // 1/1024 s
                cadence = crankCadence(revs, time, nowMs)
            }
        } catch (_: TruncatedPacket) {
        }
        return BikeSample(powerWatts = power, cadenceRpm = cadence)
    }

    private fun crankCadence(revs: Int, time: Int, nowMs: Long): Double {
        if (lastRevs >= 0) {
            val dRevs = (revs - lastRevs) and 0xFFFF
            val dTime = (time - lastEventTime) and 0xFFFF
            if (dRevs in 1..20 && dTime > 0) {
                lastCadence = dRevs * 1024.0 * 60.0 / dTime
                lastRevChangeMs = nowMs
            } else if (nowMs - lastRevChangeMs > 3000) {
                lastCadence = 0.0
            }
        } else {
            lastRevChangeMs = nowMs
        }
        lastRevs = revs
        lastEventTime = time
        return lastCadence
    }
}

object HeartRateParser {
    fun parse(data: ByteArray): Int? {
        if (data.size < 2) return null
        val flags = data[0].toInt()
        return if (flags and 0x01 == 0) data[1].toInt() and 0xFF
        else if (data.size >= 3) (data[1].toInt() and 0xFF) or ((data[2].toInt() and 0xFF) shl 8)
        else null
    }
}

/** FTMS Control Point (0x2AD9) commands. */
object FtmsCommands {
    const val OP_REQUEST_CONTROL = 0x00
    const val OP_RESET = 0x01
    const val OP_SET_TARGET_POWER = 0x05
    const val OP_START_RESUME = 0x07
    const val OP_SET_SIMULATION = 0x11
    const val OP_RESPONSE = 0x80

    const val RESULT_SUCCESS = 0x01
    const val RESULT_CONTROL_NOT_PERMITTED = 0x05

    fun requestControl() = byteArrayOf(OP_REQUEST_CONTROL.toByte())
    fun reset() = byteArrayOf(OP_RESET.toByte())
    fun startOrResume() = byteArrayOf(OP_START_RESUME.toByte())

    /** ERG mode: hold [watts]. */
    fun setTargetPower(watts: Int): ByteArray {
        val w = watts.coerceIn(0, 2000)
        return byteArrayOf(OP_SET_TARGET_POWER.toByte(), (w and 0xFF).toByte(), ((w shr 8) and 0xFF).toByte())
    }

    /**
     * Simulation mode. [cwKgPerM] is the wind resistance coefficient (0.5 * rho * CdA), which lets
     * the trainer feel lighter while you are drafting.
     */
    fun setSimulation(windMps: Double, gradePercent: Double, crr: Double, cwKgPerM: Double): ByteArray {
        val wind = (windMps * 1000).roundToInt().coerceIn(-32768, 32767)
        val grade = (gradePercent * 100).roundToInt().coerceIn(-32768, 32767)
        val crrRaw = (crr * 10000).roundToInt().coerceIn(0, 255)
        val cwRaw = (cwKgPerM * 100).roundToInt().coerceIn(0, 255)
        return byteArrayOf(
            OP_SET_SIMULATION.toByte(),
            (wind and 0xFF).toByte(), ((wind shr 8) and 0xFF).toByte(),
            (grade and 0xFF).toByte(), ((grade shr 8) and 0xFF).toByte(),
            crrRaw.toByte(),
            cwRaw.toByte(),
        )
    }

    data class Response(val requestOpCode: Int, val result: Int) {
        val success get() = result == RESULT_SUCCESS
    }

    fun parseResponse(data: ByteArray): Response? {
        if (data.size < 3 || (data[0].toInt() and 0xFF) != OP_RESPONSE) return null
        return Response(data[1].toInt() and 0xFF, data[2].toInt() and 0xFF)
    }
}
