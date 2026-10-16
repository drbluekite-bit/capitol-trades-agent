package com.velodromeghost.core.track

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

data class Point(val x: Double, val y: Double)

/**
 * A velodrome modelled as a "stadium": two straights joined by two semicircular bends, ridden
 * anticlockwise (the infield is on the rider's left).
 *
 * Race distance 0 is the finish line. Layout coordinate u runs from the start of the home
 * straight. World coordinates are metres with the origin at the infield centre, y pointing up.
 * Lateral offsets are measured outward from the inside edge of the track (the black line is at 0.2 m).
 */
class Track(
    val lapLength: Double = 250.0,
    val bendRadius: Double = 23.0,
    val width: Double = 7.0,
) {
    val bendLength: Double = PI * bendRadius
    val straightLength: Double = (lapLength - 2 * bendLength) / 2

    init {
        require(straightLength > 0) { "Lap too short for the bend radius" }
    }

    /** The finish line sits near the end of the home straight, like a real track. */
    val finishLineU: Double = straightLength * 0.8

    /** Race distance (mod lap) of the 200 m line. */
    val twoHundredLineDistance: Double = mod(lapLength - 200.0, lapLength)

    fun layoutU(raceDistance: Double): Double = mod(raceDistance + finishLineU, lapLength)

    fun position(raceDistance: Double, lateral: Double): Point = pointAtU(layoutU(raceDistance), lateral)

    fun pointAtU(uIn: Double, lateral: Double): Point {
        val u = mod(uIn, lapLength)
        val r = bendRadius + lateral
        val half = straightLength / 2
        val bend1Start = straightLength
        val back = bend1Start + bendLength
        val bend2Start = back + straightLength
        return when {
            u < bend1Start -> Point(-half + u, -r)
            u < back -> {
                val a = -PI / 2 + (u - bend1Start) / bendRadius
                Point(half + r * cos(a), r * sin(a))
            }
            u < bend2Start -> Point(half - (u - back), r)
            else -> {
                val a = PI / 2 + (u - bend2Start) / bendRadius
                Point(-half + r * cos(a), r * sin(a))
            }
        }
    }

    /** 0 on the straights, 1 in the bends, smoothly blended through the transitions. */
    fun bendness(raceDistance: Double): Double {
        val u = layoutU(raceDistance)
        val b1 = straightLength
        val b2 = 2 * straightLength + bendLength
        var best = 0.0
        for (shift in doubleArrayOf(-lapLength, 0.0, lapLength)) {
            best = max(best, ramp(u + shift, b1, b1 + bendLength))
            best = max(best, ramp(u + shift, b2, b2 + bendLength))
        }
        return best * best * (3 - 2 * best)
    }

    /** Signed curvature (1/m); positive = turning left. */
    fun curvature(raceDistance: Double): Double = bendness(raceDistance) / bendRadius

    fun bankAngleDeg(raceDistance: Double): Double =
        STRAIGHT_BANK_DEG + (BEND_BANK_DEG - STRAIGHT_BANK_DEG) * bendness(raceDistance)

    private fun ramp(u: Double, start: Double, end: Double): Double {
        val a = ((u - start) / TRANSITION + 0.5).coerceIn(0.0, 1.0)
        val b = ((end - u) / TRANSITION + 0.5).coerceIn(0.0, 1.0)
        return minOf(a, b)
    }

    companion object {
        const val TRANSITION = 14.0
        const val STRAIGHT_BANK_DEG = 12.0
        const val BEND_BANK_DEG = 42.0

        const val BLACK_LINE = 0.2
        const val RED_LINE = 0.85
        const val BLUE_LINE = 2.45

        fun mod(x: Double, m: Double): Double {
            val r = x % m
            return if (r < 0) r + m else r
        }
    }

    /** Distance from a to b along the track, wrapped into (-lap/2, lap/2]. */
    fun wrappedDelta(from: Double, to: Double): Double {
        var d = mod(to - from, lapLength)
        if (d > lapLength / 2) d -= lapLength
        return d
    }
}
