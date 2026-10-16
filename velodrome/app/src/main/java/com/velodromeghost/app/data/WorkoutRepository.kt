package com.velodromeghost.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.velodromeghost.core.workout.Workout
import com.velodromeghost.core.workout.ZwoParseException
import com.velodromeghost.core.workout.ZwoParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

data class WorkoutEntry(
    val id: String,
    val name: String,
    val description: String,
    val durationSec: Double,
    val builtIn: Boolean,
)

/** Bundled .zwo files live in assets/workouts; imported ones are copied into app storage. */
class WorkoutRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.filesDir, "workouts").apply { mkdirs() }

    fun list(): List<WorkoutEntry> {
        val builtIn = appContext.assets.list(ASSET_DIR).orEmpty()
            .filter { it.endsWith(".zwo", ignoreCase = true) }
            .sorted()
            .mapNotNull { file -> entryOrNull(ASSET_PREFIX + file, builtIn = true) }
        val imported = dir.listFiles { f -> f.isFile && f.name.endsWith(".zwo", ignoreCase = true) }.orEmpty()
            .sortedBy { it.name.lowercase() }
            .mapNotNull { f -> entryOrNull(FILE_PREFIX + f.name, builtIn = false) }
        return builtIn + imported
    }

    private fun entryOrNull(id: String, builtIn: Boolean): WorkoutEntry? = try {
        val w = load(id)
        WorkoutEntry(id, w.name, w.description, w.durationSec, builtIn)
    } catch (_: Exception) {
        null
    }

    @Throws(IOException::class, ZwoParseException::class)
    fun load(id: String): Workout = when {
        id.startsWith(ASSET_PREFIX) -> {
            val file = id.removePrefix(ASSET_PREFIX)
            appContext.assets.open("$ASSET_DIR/$file").use { ZwoParser.parse(it, file.removeSuffix(".zwo")) }
        }
        id.startsWith(FILE_PREFIX) -> {
            val f = File(dir, File(id.removePrefix(FILE_PREFIX)).name)
            f.inputStream().use { ZwoParser.parse(it, f.nameWithoutExtension) }
        }
        else -> throw IOException("Unknown workout $id")
    }

    /** Validates and copies a user-picked .zwo into app storage. */
    fun import(uri: Uri): Result<WorkoutEntry> = runCatching {
        val resolver = appContext.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "workout.zwo"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val data = input.readBytes()
            if (data.size > MAX_BYTES) throw IOException("File is too large for a .zwo workout")
            data
        } ?: throw IOException("Could not open the file")

        val workout = ZwoParser.parse(ByteArrayInputStream(bytes), displayName.substringBeforeLast('.'))

        val base = displayName.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "workout" }.take(60)
        var target = File(dir, "$base.zwo")
        var n = 2
        while (target.exists()) target = File(dir, "$base ($n).zwo").also { n++ }
        target.writeBytes(bytes)
        WorkoutEntry(FILE_PREFIX + target.name, workout.name, workout.description, workout.durationSec, builtIn = false)
    }

    fun delete(id: String): Boolean {
        if (!id.startsWith(FILE_PREFIX)) return false
        return File(dir, File(id.removePrefix(FILE_PREFIX)).name).delete()
    }

    private companion object {
        const val ASSET_DIR = "workouts"
        const val ASSET_PREFIX = "asset:"
        const val FILE_PREFIX = "file:"
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}
