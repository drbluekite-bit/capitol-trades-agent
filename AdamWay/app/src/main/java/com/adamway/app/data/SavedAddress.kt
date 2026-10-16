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
    val houseNumber: String = "",
    val houseName: String = "",
    val postcode: String = "",
    val what3words: String = "",
    val notes: String = "",
    /** Local file paths of photos attached to this address's notes, [PHOTO_PATH_DELIMITER]-separated. */
    val notePhotoPaths: String = "",
    /**
     * A precise location the user picked themselves from the address
     * search suggestions (or a what3words lookup), rather than one Adam
     * Way guessed automatically. When set, this is used directly at
     * journey time instead of re-geocoding — it's the whole point of
     * letting you confirm the exact address up front.
     */
    val resolvedLat: Double? = null,
    val resolvedLng: Double? = null,
    val lastUsedAt: Long = System.currentTimeMillis(),
) {
    val hasConfirmedLocation: Boolean
        get() = resolvedLat != null && resolvedLng != null

    val photoPathList: List<String>
        get() = notePhotoPaths.split(PHOTO_PATH_DELIMITER).filter { it.isNotBlank() }

    val displayLabel: String
        get() = when {
            houseName.isNotBlank() && houseNumber.isNotBlank() -> "$houseNumber $houseName"
            houseName.isNotBlank() -> houseName
            houseNumber.isNotBlank() && postcode.isNotBlank() -> "$houseNumber, $postcode"
            postcode.isNotBlank() -> postcode
            what3words.isNotBlank() -> "///$what3words"
            else -> "Untitled address"
        }

    /** A loose normalized identity used to recognize "the same address" again later. */
    private fun identityKey(): String = listOf(houseNumber, houseName, postcode.replace(" ", ""), what3words)
        .joinToString("|") { it.trim().lowercase() }

    fun matches(houseNumber: String, houseName: String, postcode: String, what3words: String): Boolean =
        identityKey() == SavedAddress(
            houseNumber = houseNumber,
            houseName = houseName,
            postcode = postcode,
            what3words = what3words,
        ).identityKey()

    companion object {
        const val PHOTO_PATH_DELIMITER = "|"
    }
}
