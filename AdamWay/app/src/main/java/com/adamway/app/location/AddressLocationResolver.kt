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
 * Both a what3words entry and a postal address are resolved to a precise
 * lat/lng first — via [What3WordsClient] and [NominatimClient]
 * respectively — rather than handed to Google Maps as free text. Maps'
 * own free-text waypoint geocoder turned out to be unreliable for a bare
 * house number plus postcode (it can snap to the postcode's general area
 * instead of the actual building); resolving coordinates ourselves first
 * fixes that. Free text is only used as a last-resort fallback if a
 * lookup fails (e.g. no network), so a flaky geocoding call never blocks
 * the stop from being added to the route entirely.
 */
class AddressLocationResolver(
    private val what3WordsClient: What3WordsClient,
    private val nominatimClient: NominatimClient = NominatimClient(),
) {

    suspend fun resolve(address: Address): ResolvedStop {
        if (address.what3words.isNotBlank()) {
            val w3wResult = what3WordsClient.convertToCoordinates(address.what3words)
            if (w3wResult is What3WordsResult.Success) {
                return ResolvedStop.Coordinates(address, w3wResult.location)
            }
            if (address.postalQuery.isBlank()) {
                val reason = when (w3wResult) {
                    is What3WordsResult.MissingApiKey -> "Add a what3words API key in Settings to use ///${address.what3words}"
                    is What3WordsResult.Error -> w3wResult.message
                    else -> "Couldn't resolve ///${address.what3words}"
                }
                return ResolvedStop.Unresolved(address, reason)
            }
            // No usable what3words result, but there are postal fields too — fall through and try those.
        }

        if (address.postalQuery.isNotBlank()) {
            val geocoded = nominatimClient.geocode(address.postalQuery)
            return if (geocoded is GeocodeResult.Success) {
                ResolvedStop.Coordinates(address, geocoded.location)
            } else {
                // Couldn't pin it down ourselves — let Maps try its own free-text geocoding as a fallback.
                ResolvedStop.TextQuery(address, address.postalQuery)
            }
        }

        return ResolvedStop.Unresolved(address, "No usable address details")
    }

    suspend fun resolveAll(addresses: List<Address>): List<ResolvedStop> = addresses.map { resolve(it) }
}
