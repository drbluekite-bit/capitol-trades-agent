package com.velodromeghost.core.workout

enum class SegmentKind { WARMUP, STEADY, INTERVAL_ON, INTERVAL_OFF, RAMP, COOLDOWN, FREE_RIDE, MAX_EFFORT }

/**
 * One block of a workout. Power values are fractions of FTP (1.0 = 100% FTP), exactly as
 * in a Zwift .zwo file. Power ramps linearly from [powerStart] to [powerEnd].
 */
data class Segment(
    val kind: SegmentKind,
    val durationSec: Double,
    val powerStart: Double,
    val powerEnd: Double = powerStart,
    val cadence: Int? = null,
)

data class TextEvent(val timeSec: Double, val message: String)

class Workout(
    val name: String,
    val segments: List<Segment>,
    val description: String = "",
    val author: String = "",
    textEvents: List<TextEvent> = emptyList(),
) {
    val textEvents: List<TextEvent> = textEvents.sortedBy { it.timeSec }

    private val startTimes: DoubleArray = DoubleArray(segments.size).also { starts ->
        var t = 0.0
        segments.forEachIndexed { i, s ->
            starts[i] = t
            t += s.durationSec
        }
    }

    val durationSec: Double = segments.sumOf { it.durationSec }

    /** Index of the segment active at [timeSec]; after the end the last segment is held. */
    fun segmentIndexAt(timeSec: Double): Int {
        if (segments.isEmpty()) return -1
        if (timeSec <= 0.0) return 0
        if (timeSec >= durationSec) return segments.lastIndex
        var lo = 0
        var hi = segments.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (startTimes[mid] <= timeSec) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun segmentStart(index: Int): Double = startTimes[index]

    /** Target power as a fraction of FTP. Once the workout is over the final power is held. */
    fun powerFractionAt(timeSec: Double): Double {
        val i = segmentIndexAt(timeSec)
        if (i < 0) return 0.0
        val seg = segments[i]
        if (timeSec >= durationSec) return seg.powerEnd
        val progress = if (seg.durationSec <= 0.0) 1.0
        else ((timeSec - startTimes[i]) / seg.durationSec).coerceIn(0.0, 1.0)
        return seg.powerStart + (seg.powerEnd - seg.powerStart) * progress
    }

    fun targetWatts(timeSec: Double, ftpWatts: Double): Double = powerFractionAt(timeSec) * ftpWatts

    fun cadenceAt(timeSec: Double): Int? {
        val i = segmentIndexAt(timeSec)
        return if (i < 0) null else segments[i].cadence
    }

    /** Text events with fromExclusive < time <= toInclusive. */
    fun textEventsIn(fromExclusive: Double, toInclusive: Double): List<TextEvent> =
        textEvents.filter { it.timeSec > fromExclusive && it.timeSec <= toInclusive }

    companion object {
        /** FreeRide blocks have no target, so the ghost rides them at this fraction of FTP. */
        const val FREE_RIDE_POWER = 0.65

        /** MaxEffort blocks are all-out; the ghost rides them at this fraction of FTP. */
        const val MAX_EFFORT_POWER = 1.6
    }
}
