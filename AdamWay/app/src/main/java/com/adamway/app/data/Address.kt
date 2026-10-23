package com.adamway.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single stop in the journey queue. [addressText] is a single free-text
 * line — exactly what you'd type into Google Maps' own search bar — so
 * Adam Way never tries to parse or geocode it itself; it's handed straight
 * to Maps/Waze at journey time and resolved by their own geocoder. Either
 * field is optional on its own, but at least one must be non-blank
 * (enforced by [AddressRepository]).
 */
@Entity(tableName = "addresses")
data class Address(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val addressText: String = "",
    val what3words: String = "",
    /** Position in the queue, ascending. Kept dense (0..n-1) after every reorder. */
    val position: Int,
    /** Marked true once the journey has moved past this stop. */
    val visited: Boolean = false,
    /**
     * Links to this address's entry in the persistent address book
     * ([SavedAddress]), where its notes and photos live. That record
     * outlives this queue row — it's still there next time you add the
     * same address, even after Clear Queue.
     */
    val savedAddressId: Long? = null,
) {
    val isBlank: Boolean
        get() = addressText.isBlank() && what3words.isBlank()

    /** A short label for lists. */
    val displayLabel: String
        get() = when {
            addressText.isNotBlank() -> addressText
            what3words.isNotBlank() -> "///$what3words"
            else -> "Untitled address"
        }

    val subLabel: String?
        get() = if (what3words.isNotBlank() && addressText.isNotBlank()) "///$what3words" else null
}
