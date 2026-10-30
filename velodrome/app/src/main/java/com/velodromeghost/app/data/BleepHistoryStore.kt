package com.velodromeghost.app.data

import android.content.Context
import com.velodromeghost.core.race.BleepResult
import org.json.JSONArray
import org.json.JSONObject

/** Saved bleep test results, kept on the phone so you can track fitness month to month. */
class BleepHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("bleep_history", Context.MODE_PRIVATE)

    fun load(): List<BleepResult> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BleepResult(
                    timestampMs = o.getLong("t"),
                    level = o.getInt("level"),
                    watts = o.getDouble("watts"),
                    ftpWatts = o.optInt("ftp"),
                    weightKg = o.optInt("kg"),
                    startWatts = o.optDouble("start", 0.0),
                    stepWatts = o.optDouble("step", 0.0),
                    levelLaps = o.optInt("laps", 4),
                )
            }.sortedBy { it.timestampMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun add(result: BleepResult): List<BleepResult> {
        val all = (load() + result).sortedBy { it.timestampMs }
        save(all)
        return all
    }

    fun delete(result: BleepResult): List<BleepResult> {
        val all = load().filterNot { it.timestampMs == result.timestampMs }
        save(all)
        return all
    }

    private fun save(all: List<BleepResult>) {
        val arr = JSONArray()
        all.forEach { r ->
            arr.put(
                JSONObject()
                    .put("t", r.timestampMs)
                    .put("level", r.level)
                    .put("watts", r.watts)
                    .put("ftp", r.ftpWatts)
                    .put("kg", r.weightKg)
                    .put("start", r.startWatts)
                    .put("step", r.stepWatts)
                    .put("laps", r.levelLaps)
            )
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "results"
    }
}
