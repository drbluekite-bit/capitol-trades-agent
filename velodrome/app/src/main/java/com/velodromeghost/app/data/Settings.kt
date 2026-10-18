package com.velodromeghost.app.data

import android.content.Context
import com.velodromeghost.core.race.Omnium
import com.velodromeghost.core.race.RaceMode

enum class ResistanceMode(val label: String, val help: String) {
    SIM("Track feel (sim)", "SmartSpin2k simulates a flat board track; resistance eases while you draft."),
    ERG("ERG – follow workout", "SmartSpin2k holds the workout's power for you (scaled to your FTP)."),
    OFF("No control", "Leave the resistance knob to you."),
}

enum class RaceType(val label: String, val help: String) {
    ONE_V_ONE("1 v 1 race", "Head to head against the ghost. First across the line wins."),
    POINTS_RACE(
        "Points race",
        "Sprint every few laps for 5 / 3 points, double points at the finish, +20 for lapping the ghost. Most points wins.",
    ),
    MADISON(
        "Madison",
        "Two-rider relay: you and a virtual partner take turns, swapping with a hand-sling every few laps. Points-race scoring against the ghost team.",
    ),
    INDIVIDUAL_PURSUIT(
        "Individual pursuit",
        "You and the ghost start on opposite sides of the track. Fastest time wins, or catch the ghost to win outright.",
    ),
    OMNIUM(
        "Omnium",
        "Four races in one session: scratch, tempo, elimination and points race. 40 / 38 points per race, points-race points added on top.",
    ),
}

data class AppSettings(
    val ftpWatts: Int = 200,
    val riderWeightKg: Int = 75,
    val bikeWeightKg: Int = 8,
    val raceType: RaceType = RaceType.ONE_V_ONE,
    val sprintLaps: Int = 3,
    val pointsLaps: Int = 40,
    val sprintEveryLaps: Int = 10,
    val madisonLaps: Int = 60,
    /** Laps per Madison turn, in half laps (4 = 2 laps). */
    val madisonChangeHalfLaps: Int = 4,
    val partnerStrengthPct: Int = 100,
    val pursuitLaps: Int = 16,
    val omniumFull: Boolean = false,
    val ghostStrengthPct: Int = 100,
    val constantPacePct: Int = 85,
    val ghostAttacks: Boolean = true,
    val ghostSprintPct: Int = 160,
    val ghostHeadStartM: Int = 0,
    /** null = constant pace. */
    val workoutId: String? = null,
    val resistance: ResistanceMode = ResistanceMode.SIM,
    val demoMode: Boolean = false,
    /** Open the ChatGPT app with the ride attached as soon as a race finishes. */
    val autoSendToChatGpt: Boolean = true,
    val lastDeviceAddress: String? = null,
    val lastDeviceName: String? = null,
) {
    /** The race(s) to ride, in order (more than one for the omnium). */
    fun events(): List<Omnium.Event> = when (raceType) {
        RaceType.ONE_V_ONE -> listOf(Omnium.Event(RaceMode.SPRINT_1V1, sprintLaps))
        RaceType.POINTS_RACE -> listOf(Omnium.Event(RaceMode.POINTS_RACE, pointsLaps))
        RaceType.MADISON -> listOf(Omnium.Event(RaceMode.MADISON, madisonLaps))
        RaceType.INDIVIDUAL_PURSUIT -> listOf(Omnium.Event(RaceMode.INDIVIDUAL_PURSUIT, pursuitLaps))
        RaceType.OMNIUM -> Omnium.events(omniumFull)
    }
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            ftpWatts = prefs.getInt("ftp", d.ftpWatts),
            riderWeightKg = prefs.getInt("riderKg", d.riderWeightKg),
            bikeWeightKg = prefs.getInt("bikeKg", d.bikeWeightKg),
            // Older versions stored "mode"; their sprint race "omnium" is what's now called the points race.
            raceType = enumOrNull<RaceType>(prefs.getString("raceType", null))
                ?: if (prefs.getString("mode", null) == "OMNIUM_POINTS") RaceType.POINTS_RACE else d.raceType,
            sprintLaps = prefs.getInt("sprintLaps", d.sprintLaps),
            pointsLaps = prefs.getInt("pointsLaps", prefs.getInt("omniumLaps", d.pointsLaps)),
            madisonLaps = prefs.getInt("madisonLaps", d.madisonLaps),
            madisonChangeHalfLaps = prefs.getInt("madisonChange", d.madisonChangeHalfLaps),
            partnerStrengthPct = prefs.getInt("partnerStrength", d.partnerStrengthPct),
            pursuitLaps = prefs.getInt("pursuitLaps", d.pursuitLaps),
            omniumFull = prefs.getBoolean("omniumFull", d.omniumFull),
            sprintEveryLaps = prefs.getInt("sprintEvery", d.sprintEveryLaps),
            ghostStrengthPct = prefs.getInt("ghostStrength", d.ghostStrengthPct),
            constantPacePct = prefs.getInt("constantPace", d.constantPacePct),
            ghostAttacks = prefs.getBoolean("ghostAttacks", d.ghostAttacks),
            ghostSprintPct = prefs.getInt("ghostSprint", d.ghostSprintPct),
            ghostHeadStartM = prefs.getInt("headStart", d.ghostHeadStartM),
            workoutId = prefs.getString("workout", null),
            resistance = enumOr(prefs.getString("resistance", null), d.resistance),
            demoMode = prefs.getBoolean("demo", d.demoMode),
            autoSendToChatGpt = prefs.getBoolean("autoChatGpt", d.autoSendToChatGpt),
            lastDeviceAddress = prefs.getString("deviceAddress", null),
            lastDeviceName = prefs.getString("deviceName", null),
        )
    }

    fun save(s: AppSettings) {
        prefs.edit()
            .putInt("ftp", s.ftpWatts)
            .putInt("riderKg", s.riderWeightKg)
            .putInt("bikeKg", s.bikeWeightKg)
            .putString("raceType", s.raceType.name)
            .putInt("sprintLaps", s.sprintLaps)
            .putInt("pointsLaps", s.pointsLaps)
            .putInt("madisonLaps", s.madisonLaps)
            .putInt("madisonChange", s.madisonChangeHalfLaps)
            .putInt("partnerStrength", s.partnerStrengthPct)
            .putInt("pursuitLaps", s.pursuitLaps)
            .putBoolean("omniumFull", s.omniumFull)
            .putInt("sprintEvery", s.sprintEveryLaps)
            .putInt("ghostStrength", s.ghostStrengthPct)
            .putInt("constantPace", s.constantPacePct)
            .putBoolean("ghostAttacks", s.ghostAttacks)
            .putInt("ghostSprint", s.ghostSprintPct)
            .putInt("headStart", s.ghostHeadStartM)
            .putString("workout", s.workoutId)
            .putString("resistance", s.resistance.name)
            .putBoolean("demo", s.demoMode)
            .putBoolean("autoChatGpt", s.autoSendToChatGpt)
            .putString("deviceAddress", s.lastDeviceAddress)
            .putString("deviceName", s.lastDeviceName)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        enumOrNull<T>(name) ?: default

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } }
}
