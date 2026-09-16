package com.adamway.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single stop in the journey queue. Every field is optional on its own,
 * but at least one must be non-blank (enforced by [AddressRepository]) so an
 * entry always has something Google Maps, Waze, or What3Words can resolve.
 */
@Entity(tableName = "addresses")
data class Address(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val houseNumber: String = "",
    val houseName: String = "",
    val postcode: String = "",
    val what3words: String = "",
    /** Position in the queue, ascending. Kept dense (0..n-1) after every reorder. */
    val position: Int,
    /** Marked true once the journey has moved past this stop. */
    val visited: Boolean = false,
) {
    val isBlank: Boolean
        get() = houseNumber.isBlank() && houseName.isBlank() && postcode.isBlank() && what3words.isBlank()

    /** Free-text line built from the postal fields, for use as a Google Maps search query. */
    val postalQuery: String
        get() = listOf(houseNumber, houseName, postcode)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(", ")

    /** A short label for lists: prefers the house name, falls back to whatever is set. */
    val displayLabel: String
        get() = when {
            houseName.isNotBlank() && houseNumber.isNotBlank() -> "$houseNumber $houseName"
            houseName.isNotBlank() -> houseName
            houseNumber.isNotBlank() && postcode.isNotBlank() -> "$houseNumber, $postcode"
            postalQuery.isNotBlank() -> postalQuery
            what3words.isNotBlank() -> "///$what3words"
            else -> "Untitled address"
        }

    val subLabel: String?
        get() {
            val parts = mutableListOf<String>()
            if (postcode.isNotBlank() && displayLabel != postcode) parts += postcode
            if (what3words.isNotBlank()) parts += "///$what3words"
            return parts.joinToString(" • ").ifBlank { null }
        }
}
