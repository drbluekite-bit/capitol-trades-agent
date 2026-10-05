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
    /**
     * Links to this address's entry in the persistent address book
     * ([SavedAddress]), where its notes and photos live. That record
     * outlives this queue row — it's still there next time you add the
     * same address, even after Clear Queue.
     */
    val savedAddressId: Long? = null,
) {
    val isBlank: Boolean
        get() = houseNumber.isBlank() && houseName.isBlank() && postcode.isBlank() && what3words.isBlank()

    /**
     * Free-text line built from the postal fields, for use as a Google Maps search query.
     *
     * Google's geocoder treats a comma as a separator between distinct place
     * components, so joining everything with ", " (e.g. "24, BL8 3LB") makes
     * it read "24" as its own place — which isn't one — and it can fall back
     * to an approximate match instead of the actual house. A bare house
     * number next to a postcode needs to read as a single compound address
     * ("24 BL8 3LB", space-joined) the way someone would actually type it;
     * a house number with a street name is joined the same way and then
     * comma-separated from the postcode ("24 Sandybrook Close, BL8 3LB").
     */
    val postalQuery: String
        get() {
            val number = houseNumber.trim()
            val street = houseName.trim()
            val pc = postcode.trim()
            val numberAndStreet = listOf(number, street).filter { it.isNotEmpty() }.joinToString(" ")
            return when {
                street.isNotEmpty() && pc.isNotEmpty() -> "$numberAndStreet, $pc"
                street.isNotEmpty() -> numberAndStreet
                pc.isNotEmpty() && number.isNotEmpty() -> "$number $pc"
                pc.isNotEmpty() -> pc
                else -> number
            }
        }

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
