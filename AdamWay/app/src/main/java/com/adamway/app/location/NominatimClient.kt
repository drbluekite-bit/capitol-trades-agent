package com.adamway.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

/**
 * Resolves a free-text postal address to a precise latitude/longitude using
 * OpenStreetMap's free Nominatim search API — no API key, no billing
 * account. This exists because Google Maps' own free-text waypoint
 * geocoder is unreliable for a bare house number plus postcode (no street
 * name): it can snap to the postcode's general area instead of the actual
 * building. Resolving to coordinates ourselves first, the same way
 * what3words addresses already are, sidesteps that.
 *
 * Results are restricted to Great Britain (see [geocode]) since this app's
 * address parsing already assumes UK postcodes.
 */
class NominatimClient {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class NominatimResult(val lat: String, val lon: String)

    suspend fun geocode(query: String): GeocodeResult = withContext(Dispatchers.IO) {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl()
            .newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", "1")
            // Without a country restriction, a short or slightly ambiguous
            // query (e.g. just a house number/postcode fragment that didn't
            // parse quite right) can match Nominatim's best global guess
            // instead of a "no match" — which can be a street on the other
            // side of the world. Adam Way's address parsing already assumes
            // UK postcodes, so biasing/limiting results to Great Britain
            // rules that out rather than relying on ranking alone.
            .addQueryParameter("countrycodes", "gb")
            .build()

        val request = Request.Builder()
            .url(url)
            // Nominatim's usage policy requires a descriptive User-Agent
            // identifying the app rather than a generic/default one.
            .header("User-Agent", "AdamWay-Android-App/1.0 (personal journey-planning app, not for redistribution)")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext GeocodeResult.Error("Geocoding lookup failed (HTTP ${response.code})")
                }
                val body = response.body?.string().orEmpty()
                val results = json.decodeFromString<List<NominatimResult>>(body)
                val first = results.firstOrNull()
                    ?: return@withContext GeocodeResult.Error("No map match found for \"$query\"")
                GeocodeResult.Success(LatLng(first.lat.toDouble(), first.lon.toDouble()))
            }
        } catch (e: IOException) {
            GeocodeResult.Error(e.message ?: "Network error contacting the geocoding service")
        } catch (e: Exception) {
            GeocodeResult.Error(e.message ?: "Couldn't parse the geocoding result")
        }
    }
}
