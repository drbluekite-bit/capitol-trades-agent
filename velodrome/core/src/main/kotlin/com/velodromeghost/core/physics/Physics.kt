package com.velodromeghost.core.physics

import kotlin.math.max

/** Rider + bike parameters for an indoor board track (no wind, no gradient). */
data class RiderParams(
    val totalMassKg: Double,
    val cdA: Double = 0.24,
    val crr: Double = 0.0025,
    val airDensity: Double = 1.18,
    val drivetrainEfficiency: Double = 0.977,
)

object Physics {
    const val G = 9.81
    private const val MIN_DRIVE_SPEED = 1.5
    private const val MAX_ACCEL = 4.0
    private const val MAX_DECEL = 3.0
    private const val ROTATIONAL_MASS_FACTOR = 1.03

    /** Fraction of normal aerodynamic drag felt directly behind another rider. */
    const val FULL_DRAFT_FACTOR = 0.65
    const val DRAFT_RANGE_METERS = 5.0

    /** Acceleration in m/s² given power at the pedals and a drag multiplier (1.0 = no draft). */
    fun acceleration(speed: Double, powerWatts: Double, p: RiderParams, dragFactor: Double = 1.0): Double {
        val drive = max(powerWatts, 0.0) * p.drivetrainEfficiency / max(speed, MIN_DRIVE_SPEED)
        val aero = 0.5 * p.airDensity * p.cdA * dragFactor * speed * speed
        val rolling = if (speed > 0.0) p.crr * p.totalMassKg * G else 0.0
        val a = (drive - aero - rolling) / (p.totalMassKg * ROTATIONAL_MASS_FACTOR)
        return a.coerceIn(-MAX_DECEL, MAX_ACCEL)
    }

    fun step(speed: Double, powerWatts: Double, p: RiderParams, dragFactor: Double, dt: Double): Double =
        max(0.0, speed + acceleration(speed, powerWatts, p, dragFactor) * dt)

    /** Speed (m/s) a rider settles at when holding [powerWatts]. */
    fun steadyStateSpeed(powerWatts: Double, p: RiderParams, dragFactor: Double = 1.0): Double {
        if (powerWatts <= 0.0) return 0.0
        var lo = 0.0
        var hi = 40.0
        repeat(60) {
            val mid = (lo + hi) / 2
            val resist = (0.5 * p.airDensity * p.cdA * dragFactor * mid * mid + p.crr * p.totalMassKg * G) * mid
            if (resist < powerWatts * p.drivetrainEfficiency) lo = mid else hi = mid
        }
        return (lo + hi) / 2
    }

    /**
     * Drag multiplier for a rider whose wheel is [gapAheadMeters] behind the rider in front.
     * Full draft within 1 m, fading out linearly to nothing at 5 m.
     */
    fun draftFactor(gapAheadMeters: Double): Double = when {
        gapAheadMeters <= 0.0 || gapAheadMeters >= DRAFT_RANGE_METERS -> 1.0
        gapAheadMeters <= 1.0 -> FULL_DRAFT_FACTOR
        else -> FULL_DRAFT_FACTOR +
            (gapAheadMeters - 1.0) / (DRAFT_RANGE_METERS - 1.0) * (1.0 - FULL_DRAFT_FACTOR)
    }

    /** Track bikes are fixed gear: cadence follows speed. 50x15 on a 2.1 m wheel = 7.0 m per rev. */
    const val TRACK_GEAR_DEVELOPMENT_METERS = 7.0

    fun cadenceForSpeed(speedMps: Double): Double = speedMps / TRACK_GEAR_DEVELOPMENT_METERS * 60.0
}
