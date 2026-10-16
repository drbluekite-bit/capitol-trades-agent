package com.adamway.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed class GeocodeResult {
    data class Success(val location: LatLng) : GeocodeResult()
    data class Error(val message: String) : GeocodeResult()
}

/** One candidate match from an address search, for the user to confirm. */
data class AddressSuggestion(
    val displayName: String,
    val location: LatLng,
    val houseNumber: String,
    val road: String,
    val postcode: String,
)

/**
 * Looks up addresses using OpenStreetMap's free Nominatim search API — no
 * API key, no billing account.
 *
 * [suggest] is the primary way this is used: it returns several candidate
 * addresses for the user to pick the exact one from, the same way typing
 * into Google Maps' own search bar brings up a results list — so Adam Way
 * is never the one silently guessing which match is correct.
 *
 * [geocode] (a single best-guess result) exists only as a fallback for
 * addresses that were added before this existed, or that the user never
 * confirmed via [suggest]; it's inherently less reliable, since nothing
 * double-checks its single guess against what the user actually meant.
 *
 * Results are restricted to Great Britain, since this app's address
 * parsing already assumes UK postcodes — without that restriction, a
 * short or ambiguous query can match Nominatim's best global guess
 * instead of a clean "no match".
 */
class NominatimClient {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class NominatimAddress(
        @SerialName("house_number") val houseNumber: String? = null,
        val road: String? = null,
        val postcode: String? = null,
    )

    @Serializable
    private data class NominatimResult(
        val lat: String,
        val lon: String,
        @SerialName("display_name") val displayName: String = "",
        val address: NominatimAddress? = null,
    )

    private suspend fun search(query: String, limit: Int): List<NominatimResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("limit", limit.toString())
            // Without a country restriction, a short or slightly ambiguous
            // query can match Nominatim's best global guess instead of a
            // "no match" — which can be a street on the other side of the
            // world. Adam Way's address parsing already assumes UK
            // postcodes, so biasing/limiting results to Great Britain rules
            // that out rather than relying on ranking alone.
            .addQueryParameter("countrycodes", "gb")
            .build()

        val request = Request.Builder()
            .url(url)
            // Nominatim's usage policy requires a descriptive User-Agent
            // identifying the app rather than a generic/default one.
            .header("User-Agent", "AdamWay-Android-App/1.0 (personal journey-planning app, not for redistribution)")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string().orEmpty()
            json.decodeFromString<List<NominatimResult>>(body)
        }
    }

    /** Up to [limit] candidate addresses matching [query], most relevant first. */
    suspend fun suggest(query: String, limit: Int = 5): List<AddressSuggestion> = try {
        search(query, limit).map {
            AddressSuggestion(
                displayName = it.displayName,
                location = LatLng(it.lat.toDouble(), it.lon.toDouble()),
                houseNumber = it.address?.houseNumber.orEmpty(),
                road = it.address?.road.orEmpty(),
                postcode = it.address?.postcode.orEmpty(),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** A single best-guess coordinate for [query]. See the fallback-only caveat in the class doc. */
    suspend fun geocode(query: String): GeocodeResult {
        return try {
            val first = search(query, limit = 1).firstOrNull()
                ?: return GeocodeResult.Error("No map match found for \"$query\"")
            GeocodeResult.Success(LatLng(first.lat.toDouble(), first.lon.toDouble()))
        } catch (e: IOException) {
            GeocodeResult.Error(e.message ?: "Network error contacting the geocoding service")
        } catch (e: Exception) {
            GeocodeResult.Error(e.message ?: "Couldn't parse the geocoding result")
        }
    }
}
