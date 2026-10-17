package com.velodromeghost.core.fit

/** Per-second ride data as CSV, which chat assistants and spreadsheets read easily. */
object RideCsv {
    const val HEADER = "elapsed_s,power_w,cadence_rpm,heart_rate_bpm,speed_kmh,distance_m"

    fun write(samples: List<RideSample>): String = buildString {
        appendLine(HEADER)
        for (s in samples) {
            append(s.elapsedSec).append(',')
            append(s.powerWatts).append(',')
            append(s.cadenceRpm ?: "").append(',')
            append(s.heartRate ?: "").append(',')
            append(String.format(java.util.Locale.US, "%.1f", s.speedMps * 3.6)).append(',')
            append(String.format(java.util.Locale.US, "%.1f", s.distanceM))
            appendLine()
        }
    }
}
