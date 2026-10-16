package com.velodromeghost.app.data

import android.content.Context
import com.velodromeghost.core.race.RaceMode

enum class ResistanceMode(val label: String, val help: String) {
    SIM("Track feel (sim)", "SmartSpin2k simulates a flat board track; resistance eases while you draft."),
    ERG("ERG – follow workout", "SmartSpin2k holds the workout's power for you (scaled to your FTP)."),
    OFF("No control", "Leave the resistance knob to you."),
}

data class AppSettings(
    val ftpWatts: Int = 200,
    val riderWeightKg: Int = 75,
    val bikeWeightKg: Int = 8,
    val mode: RaceMode = RaceMode.SPRINT_1V1,
    val sprintLaps: Int = 3,
    val omniumLaps: Int = 40,
    val sprintEveryLaps: Int = 10,
    val ghostStrengthPct: Int = 100,
    val constantPacePct: Int = 85,
    val ghostAttacks: Boolean = true,
    val ghostSprintPct: Int = 160,
    val ghostHeadStartM: Int = 0,
    /** null = constant pace. */
    val workoutId: String? = null,
    val resistance: ResistanceMode = ResistanceMode.SIM,
    val demoMode: Boolean = false,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
) {
    val laps: Int get() = if (mode == RaceMode.SPRINT_1V1) sprintLaps else omniumLaps
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            ftpWatts = prefs.getInt("ftp", d.ftpWatts),
            riderWeightKg = prefs.getInt("riderKg", d.riderWeightKg),
            bikeWeightKg = prefs.getInt("bikeKg", d.bikeWeightKg),
            mode = enumOr(prefs.getString("mode", null), d.mode),
            sprintLaps = prefs.getInt("sprintLaps", d.sprintLaps),
            omniumLaps = prefs.getInt("omniumLaps", d.omniumLaps),
            sprintEveryLaps = prefs.getInt("sprintEvery", d.sprintEveryLaps),
            ghostStrengthPct = prefs.getInt("ghostStrength", d.ghostStrengthPct),
            constantPacePct = prefs.getInt("constantPace", d.constantPacePct),
            ghostAttacks = prefs.getBoolean("ghostAttacks", d.ghostAttacks),
            ghostSprintPct = prefs.getInt("ghostSprint", d.ghostSprintPct),
            ghostHeadStartM = prefs.getInt("headStart", d.ghostHeadStartM),
            workoutId = prefs.getString("workout", null),
            resistance = enumOr(prefs.getString("resistance", null), d.resistance),
            demoMode = prefs.getBoolean("demo", d.demoMode),
            lastDeviceAddress = prefs.getString("deviceAddress", null),
            lastDeviceName = prefs.getString("deviceName", null),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putInt("ftp", s.ftpWatts)
            .putInt("riderKg", s.riderWeightKg)
            .putInt("bikeKg", s.bikeWeightKg)
            .putString("mode", s.mode.name)
            .putInt("sprintLaps", s.sprintLaps)
            .putInt("omniumLaps", s.omniumLaps)
            .putInt("sprintEvery", s.sprintEveryLaps)
            .putInt("ghostStrength", s.ghostStrengthPct)
            .putInt("constantPace", s.constantPacePct)
            .putBoolean("ghostAttacks", s.ghostAttacks)
            .putInt("ghostSprint", s.ghostSprintPct)
            .putInt("headStart", s.ghostHeadStartM)
            .putString("workout", s.workoutId)
            .putString("resistance", s.resistance.name)
            .putBoolean("demo", s.demoMode)
            .putString("deviceAddress", s.lastDeviceAddress)
            .putString("deviceName", s.lastDeviceName)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
