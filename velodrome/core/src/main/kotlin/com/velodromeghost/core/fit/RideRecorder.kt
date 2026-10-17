package com.velodromeghost.core.fit

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** One per-second data point of your ride. */
data class RideSample(
    val elapsedSec: Int,
    val powerWatts: Int,
    val cadenceRpm: Int?,
    val heartRate: Int?,
    val speedMps: Double,
    val distanceM: Double,
)

data class RideSummary(
    val durationSec: Double,
    val distanceM: Double,
    /** Time-weighted average power, zeros included (like Garmin / Strava "average power"). */
    val avgPowerWatts: Double,
    val maxPowerWatts: Int,
    /** Average of non-zero cadence readings (coasting doesn't drag it down). */
    val avgCadenceRpm: Double?,
    val maxCadenceRpm: Int?,
    val avgHeartRate: Int?,
    val maxHeartRate: Int?,
    val avgSpeedMps: Double,
    val maxSpeedMps: Double,
    /** Total work = power integrated over time ("total watts"), in joules. */
    val totalWorkJoules: Double,
    val kcal: Double,
) {
    val totalWorkKj: Double get() = totalWorkJoules / 1000.0
}

/**
 * Collects the ride while racing: integrates work every frame and keeps one sample per second
 * of race time (pauses don't count, because race time stops while paused).
 */
class RideRecorder {
    private val _samples = mutableListOf<RideSample>()
    val samples: List<RideSample> get() = _samples

    private var workJoules = 0.0
    private var lastElapsed = 0.0
    private var nextSecond = 0
    private var lastDistance = 0.0
    private var maxSpeed = 0.0

    val isEmpty: Boolean get() = _samples.isEmpty()

    fun update(
        elapsedSec: Double,
        powerWatts: Double,
        cadenceRpm: Double?,
        heartRate: Int?,
        speedMps: Double,
        distanceM: Double,
    ) {
        val dt = elapsedSec - lastElapsed
        if (dt > 0) {
            workJoules += max(0.0, powerWatts) * dt
            lastElapsed = elapsedSec
        }
        lastDistance = max(lastDistance, distanceM)
        maxSpeed = max(maxSpeed, speedMps)
        if (elapsedSec >= nextSecond) {
            _samples += RideSample(
                elapsedSec = nextSecond,
                powerWatts = max(0.0, powerWatts).roundToInt(),
                cadenceRpm = cadenceRpm?.roundToInt()?.coerceAtLeast(0),
                heartRate = heartRate?.takeIf { it > 0 },
                speedMps = speedMps,
                distanceM = distanceM,
            )
            nextSecond = floor(elapsedSec).toInt() + 1
        }
    }

    fun summary(): RideSummary {
        val duration = lastElapsed
        val cadences = _samples.mapNotNull { it.cadenceRpm }.filter { it > 0 }
        val hrs = _samples.mapNotNull { it.heartRate }
        return RideSummary(
            durationSec = duration,
            distanceM = lastDistance,
            avgPowerWatts = if (duration > 0) workJoules / duration else 0.0,
            maxPowerWatts = _samples.maxOfOrNull { it.powerWatts } ?: 0,
            avgCadenceRpm = cadences.takeIf { it.isNotEmpty() }?.average(),
            maxCadenceRpm = cadences.maxOrNull(),
            avgHeartRate = hrs.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            maxHeartRate = hrs.maxOrNull(),
            avgSpeedMps = if (duration > 0) lastDistance / duration else 0.0,
            maxSpeedMps = maxSpeed,
            totalWorkJoules = workJoules,
            kcal = kcalFromWork(workJoules),
        )
    }

    companion object {
        /** Metabolic energy assuming ~24% gross efficiency (the usual bike-computer estimate, ≈ 1 kcal per kJ). */
        fun kcalFromWork(joules: Double): Double = joules / 4184.0 / 0.24
    }
}
