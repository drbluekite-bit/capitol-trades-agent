package com.adamway.app.location

import com.adamway.app.data.Address
import com.adamway.app.data.SavedAddressRepository

sealed class ResolvedStop {
    data class Coordinates(val address: Address, val latLng: LatLng) : ResolvedStop()
    data class TextQuery(val address: Address, val query: String) : ResolvedStop()
    data class Unresolved(val address: Address, val reason: String) : ResolvedStop()
}

/**
 * Turns each queued [Address] into something Google Maps can navigate to.
 *
 * Priority order:
 * 1. A location the user explicitly **confirmed** themselves — by picking
 *    an address-search suggestion when adding the stop — stored on its
 *    linked [com.adamway.app.data.SavedAddress]. This is the only source
 *    that's actually certain: nothing is being guessed.
 * 2. A what3words entry, resolved via [What3WordsClient].
 * 3. A postal address, resolved via [NominatimClient] as a best-guess
 *    automatic geocode — used only when the stop was never confirmed
 *    (e.g. added before this existed). This is inherently less reliable
 *    than (1), since nothing double-checks its single guess.
 *
 * Free text is only used as a last-resort fallback if a lookup fails
 * (e.g. no network), so a flaky geocoding call never blocks the stop from
 * being added to the route entirely.
 */
class AddressLocationResolver(
    private val what3WordsClient: What3WordsClient,
    private val savedAddressRepository: SavedAddressRepository,
    private val nominatimClient: NominatimClient = NominatimClient(),
) {

    suspend fun resolve(address: Address): ResolvedStop {
        val confirmed = address.savedAddressId?.let { savedAddressRepository.getById(it) }
        if (confirmed != null && confirmed.hasConfirmedLocation) {
            return ResolvedStop.Coordinates(address, LatLng(confirmed.resolvedLat!!, confirmed.resolvedLng!!))
        }

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
