package com.velodromeghost.core.race

import kotlin.math.abs

/** One saved bleep test result. */
data class BleepResult(
    val timestampMs: Long,
    /** Levels completed: the test score. */
    val level: Int,
    /** Power of the last level completed. */
    val watts: Double,
    val ftpWatts: Int,
    val weightKg: Int,
    /** Test settings, so like is compared with like. */
    val startWatts: Double,
    val stepWatts: Double,
    val levelLaps: Int,
) {
    val wattsPerKg: Double get() = if (weightKg > 0) watts / weightKg else 0.0
    val sameProtocolAs: (BleepResult) -> Boolean
        get() = { o -> o.startWatts == startWatts && o.stepWatts == stepWatts && o.levelLaps == levelLaps }
}

enum class BleepTrendDirection(val label: String) {
    FIRST("First test - this is your baseline"),
    IMPROVING("Getting fitter"),
    PLATEAU("Holding steady"),
    REGRESSING("Dropping off"),
}

data class BleepTrend(
    val direction: BleepTrendDirection,
    /** Change in levels since the previous comparable test. */
    val levelChange: Int,
    /** Change in last-level power since the previous comparable test. */
    val wattsChange: Double,
    val previous: BleepResult?,
    val best: BleepResult?,
    /** Average change in levels per test over (up to) the last four comparable tests. */
    val recentSlope: Double,
)

object BleepTest {
    /**
     * How [latest] compares with the previous test ridden with the same settings: at least one level
     * up is improving, at least one down is regressing, otherwise holding steady. [BleepTrend.recentSlope]
     * gives the longer-term direction over the last few tests.
     */
    fun trend(history: List<BleepResult>, latest: BleepResult): BleepTrend {
        val comparable = history.filter { it !== latest && latest.sameProtocolAs(it) && it.timestampMs <= latest.timestampMs }
            .sortedBy { it.timestampMs }
        val previous = comparable.lastOrNull()
        val best = (comparable + latest).maxWithOrNull(compareBy<BleepResult> { it.level }.thenBy { it.timestampMs })
        if (previous == null) {
            return BleepTrend(BleepTrendDirection.FIRST, 0, 0.0, null, best, 0.0)
        }
        val window = (comparable + latest).takeLast(4)
        val slope = (window.last().level - window.first().level).toDouble() / (window.size - 1)
        val change = latest.level - previous.level
        val direction = when {
            change >= 1 -> BleepTrendDirection.IMPROVING
            change <= -1 -> BleepTrendDirection.REGRESSING
            else -> BleepTrendDirection.PLATEAU
        }
        return BleepTrend(direction, change, latest.watts - previous.watts, previous, best, slope)
    }

    /** "+2 levels (+20 W)", "no change", "-1 level (-10 W)". */
    fun describeChange(t: BleepTrend): String = when {
        t.previous == null -> "first test"
        t.levelChange == 0 -> "same level as last time"
        else -> {
            val n = abs(t.levelChange)
            val sign = if (t.levelChange > 0) "+" else "-"
            "$sign$n level${if (n == 1) "" else "s"} (${if (t.wattsChange >= 0) "+" else ""}${t.wattsChange.toInt()} W)"
        }
    }
}
