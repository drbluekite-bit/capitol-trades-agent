package com.adamway.app.location

import com.adamway.app.data.Address

sealed class ResolvedStop {
    data class Coordinates(val address: Address, val latLng: LatLng) : ResolvedStop()
    data class TextQuery(val address: Address, val query: String) : ResolvedStop()
    data class Unresolved(val address: Address, val reason: String) : ResolvedStop()
}

/**
 * Turns each queued [Address] into something Google Maps can navigate to.
 *
 * Priority order:
 * 1. A what3words entry, resolved to coordinates via [What3WordsClient] —
 *    reliable, since it's a dedicated lookup rather than a guess.
 * 2. The free-text address line, handed to Maps/Waze exactly as typed so
 *    *their* geocoder resolves it — the same thing that happens when you
 *    type an address into Google Maps' own search bar. Adam Way doesn't
 *    try to geocode postal addresses itself: an earlier version did (first
 *    naively, then via OpenStreetMap's free Nominatim search), and both
 *    could still resolve to the wrong place for a short or ambiguous
 *    address — including once sending a Rotherham address to Mongolia, and
 *    another time landing on a postcode's centroid rather than the actual
 *    house. Google's and Waze's own geocoders have far better UK address
 *    coverage than either of those attempts did.
 */
class AddressLocationResolver(private val what3WordsClient: What3WordsClient) {

    suspend fun resolve(address: Address): ResolvedStop {
        if (address.what3words.isNotBlank()) {
            val w3wResult = what3WordsClient.convertToCoordinates(address.what3words)
            if (w3wResult is What3WordsResult.Success) {
                return ResolvedStop.Coordinates(address, w3wResult.location)
            }
            if (address.addressText.isBlank()) {
                val reason = when (w3wResult) {
                    is What3WordsResult.MissingApiKey -> "Add a what3words API key in Settings to use ///${address.what3words}"
                    is What3WordsResult.Error -> w3wResult.message
                    else -> "Couldn't resolve ///${address.what3words}"
                }
                return ResolvedStop.Unresolved(address, reason)
            }
            // No usable what3words result, but there's an address line too — fall through and use that.
        }

        if (address.addressText.isNotBlank()) {
            return ResolvedStop.TextQuery(address, address.addressText)
        }

        return ResolvedStop.Unresolved(address, "No usable address details")
    }

    suspend fun resolveAll(addresses: List<Address>): List<ResolvedStop> = addresses.map { resolve(it) }
}
