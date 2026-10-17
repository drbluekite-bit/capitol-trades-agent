package com.adamway.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Address-text suggestions only, sourced from OpenStreetMap's free Nominatim
 * search API — no API key, no billing account. This exists purely to give
 * the single address field a Google-Maps-search-bar-style autocomplete
 * dropdown while typing; it is deliberately **text-only** and never returns
 * (or lets anything trust) a coordinate.
 *
 * An earlier version of this app stored a coordinate from a tapped
 * suggestion and used it directly for navigation. That went wrong for UK
 * residential addresses more than once — Nominatim's free data is often
 * only mapped at postcode level rather than house level, so a "confirmed"
 * pick could still turn out to be a postcode's centroid rather than the
 * real house. So navigation never trusts anything from here: whatever text
 * ends up in the address field (typed, spoken, scanned, or picked from a
 * suggestion here) is handed to Google Maps/Waze as-is at journey time, and
 * *their* geocoder resolves it. This client only helps you type or pick
 * that text faster and more completely (postcode included).
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
        @SerialName("display_name") val displayName: String = "",
        val address: NominatimAddress? = null,
    )

    /** Up to [limit] candidate address lines matching [query], most relevant first — text only. */
    suspend fun suggest(query: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "https://nominatim.openstreetmap.org/search".toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("addressdetails", "1")
                .addQueryParameter("limit", limit.toString())
                // Adam Way's address parsing already assumes UK postcodes
                // throughout, so restricting to Great Britain avoids a
                // short/ambiguous query surfacing an irrelevant suggestion
                // from the other side of the world.
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
                    .map { formatSuggestion(it) }
                    .filter { it.isNotBlank() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatSuggestion(result: NominatimResult): String {
        val numberAndRoad = listOf(result.address?.houseNumber.orEmpty(), result.address?.road.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val postcode = result.address?.postcode.orEmpty()
        return when {
            numberAndRoad.isNotBlank() && postcode.isNotBlank() -> "$numberAndRoad, $postcode"
            numberAndRoad.isNotBlank() -> numberAndRoad
            else -> result.displayName
        }
    }
}
