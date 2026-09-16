package com.adamway.app.location

import com.adamway.app.data.Address

sealed class ResolvedStop {
    data class Coordinates(val address: Address, val latLng: LatLng) : ResolvedStop()
    data class TextQuery(val address: Address, val query: String) : ResolvedStop()
    data class Unresolved(val address: Address, val reason: String) : ResolvedStop()
}

/**
 * Turns each queued [Address] into something Google Maps can navigate to:
 * a what3words entry is resolved to a lat/lng via [What3WordsClient]; a
 * postal address is passed through as free text for Maps to geocode itself
 * (no geocoding API call needed on our side).
 */
class AddressLocationResolver(private val what3WordsClient: What3WordsClient) {

    suspend fun resolve(address: Address): ResolvedStop {
        if (address.what3words.isNotBlank()) {
            when (val result = what3WordsClient.convertToCoordinates(address.what3words)) {
                is What3WordsResult.Success -> return ResolvedStop.Coordinates(address, result.location)
                is What3WordsResult.MissingApiKey -> {
                    if (address.postalQuery.isNotBlank()) {
                        return ResolvedStop.TextQuery(address, address.postalQuery)
                    }
                    return ResolvedStop.Unresolved(address, "Add a what3words API key in Settings to use ///${address.what3words}")
                }
                is What3WordsResult.Error -> {
                    if (address.postalQuery.isNotBlank()) {
                        return ResolvedStop.TextQuery(address, address.postalQuery)
                    }
                    return ResolvedStop.Unresolved(address, result.message)
                }
            }
        }
        if (address.postalQuery.isNotBlank()) {
            return ResolvedStop.TextQuery(address, address.postalQuery)
        }
        return ResolvedStop.Unresolved(address, "No usable address details")
    }

    suspend fun resolveAll(addresses: List<Address>): List<ResolvedStop> = addresses.map { resolve(it) }
}
