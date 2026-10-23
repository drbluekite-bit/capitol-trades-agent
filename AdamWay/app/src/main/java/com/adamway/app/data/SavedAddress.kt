package com.adamway.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Adam Way's "memory" of an address: it persists even after the address is
 * removed from the current queue (including Clear Queue), so the next time
 * you add the same place it can be suggested, along with whatever notes and
 * photos you attached to it.
 */
@Entity(tableName = "saved_addresses")
data class SavedAddress(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val addressText: String = "",
    val what3words: String = "",
    val notes: String = "",
    /** Local file paths of photos attached to this address's notes, [PHOTO_PATH_DELIMITER]-separated. */
    val notePhotoPaths: String = "",
    /**
     * A name you gave this place so it can be added with a single tap from
     * the Queue screen — "Thornhill House", a school run, etc. Null/blank
     * means this is just an ordinary remembered address, not a favorite.
     */
    val favoriteLabel: String? = null,
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    val photoPathList: List<String>
        get() = notePhotoPaths.split(PHOTO_PATH_DELIMITER).filter { it.isNotBlank() }

    val displayLabel: String
        get() = when {
            addressText.isNotBlank() -> addressText
            what3words.isNotBlank() -> "///$what3words"
            else -> "Untitled address"
        }

    /** A loose normalized identity used to recognize "the same address" again later. */
    private fun identityKey(): String = listOf(addressText, what3words)
        .joinToString("|") { it.trim().lowercase() }

    fun matches(addressText: String, what3words: String): Boolean =
        identityKey() == SavedAddress(addressText = addressText, what3words = what3words).identityKey()

    companion object {
        const val PHOTO_PATH_DELIMITER = "|"
    }
}
