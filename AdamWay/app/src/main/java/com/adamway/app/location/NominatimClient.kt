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
    private data class NominatimResult(
        @SerialName("display_name") val displayName: String = "",
    )

    /** Up to [limit] candidate address lines matching [query], most relevant first — text only. */
    suspend fun suggest(query: String, limit: Int = 5): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        // Try strictly within South & West Yorkshire first (see
        // searchOnce's doc); if that finds nothing, retry across all of
        // GB rather than leaving the suggestion list empty — a real match
        // just outside the box, or one Nominatim's bounding filter missed
        // for some other reason, is still better shown than hidden.
        val bounded = searchOnce(query, limit, bounded = true)
        if (bounded.isNotEmpty()) bounded else searchOnce(query, limit, bounded = false)
    }

    private suspend fun searchOnce(query: String, limit: Int, bounded: Boolean): List<String> {
        try {
            val urlBuilder = "https://nominatim.openstreetmap.org/search".toHttpUrl()
                .newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("format", "jsonv2")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("countrycodes", "gb")
            if (bounded) {
                // Adam Way is used primarily around South & West Yorkshire
                // (Sheffield/Rotherham/Doncaster/Barnsley/Leeds/Bradford/
                // Wakefield/Huddersfield/Halifax). Without this, a query
                // that textually matches a street or business name
                // elsewhere in Great Britain could rank above — or be the
                // *only* match for — the actually-intended local one,
                // which is exactly what was happening ("miles away", or
                // just one irrelevant suggestion). bounded=1 makes this a
                // hard restriction rather than just a ranking nudge.
                // Adjust this box here if the app's home area changes.
                urlBuilder
                    .addQueryParameter("viewbox", "-2.05,53.95,-0.85,53.25")
                    .addQueryParameter("bounded", "1")
            }
            val url = urlBuilder.build()

            val request = Request.Builder()
                .url(url)
                // Nominatim's usage policy requires a descriptive User-Agent
                // identifying the app rather than a generic/default one.
                .header("User-Agent", "AdamWay-Android-App/1.0 (personal journey-planning app, not for redistribution)")
                .get()
                .build()

            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string().orEmpty()
                json.decodeFromString<List<NominatimResult>>(body)
                    .map { formatSuggestion(it.displayName) }
                    .filter { it.isNotBlank() }
                    .distinct()
            }
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Nominatim's own [displayName] is usually the clearest thing to show —
     * unlike a line rebuilt from just house-number/road/postcode, it already
     * leads with a business or school's actual name when the match is one
     * (e.g. "Tesco Extra, Pontefract Road, Stairfoot, Barnsley, ... S70 3NS"
     * rather than just "Pontefract Road, S70 3NS", which gave no way to
     * tell it apart from any other address on that road). This just trims
     * the redundant country/constituent-country suffix Nominatim always
     * appends.
     */
    private fun formatSuggestion(displayName: String): String {
        val redundant = setOf("United Kingdom", "England", "Scotland", "Wales", "Northern Ireland")
        return displayName.split(", ").filterNot { it in redundant }.joinToString(", ")
    }
}
