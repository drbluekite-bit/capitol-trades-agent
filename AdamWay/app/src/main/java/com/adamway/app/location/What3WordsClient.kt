package com.adamway.app.location

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

data class LatLng(val lat: Double, val lng: Double)

sealed class What3WordsResult {
    data class Success(val location: LatLng) : What3WordsResult()
    object MissingApiKey : What3WordsResult()
    data class Error(val message: String) : What3WordsResult()
}

/**
 * Talks only to api.what3words.com, and only when a queue entry has a
 * what3words value. This is the sole outbound network call in the app; it
 * carries the three words and the user's own free API key, nothing else.
 */
class What3WordsClient(private val apiKeyProvider: () -> String) {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class CoordinatesResponse(val lat: Double, val lng: Double)

    @Serializable
    private data class ConvertResponse(
        val coordinates: CoordinatesResponse? = null,
        val error: ErrorBody? = null,
    )

    @Serializable
    private data class ErrorBody(val code: String? = null, val message: String? = null)

    suspend fun convertToCoordinates(words: String): What3WordsResult = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) return@withContext What3WordsResult.MissingApiKey

        val cleaned = words.trim().removePrefix("///")
        val url = "https://api.what3words.com/v3/convert-to-coordinates".toHttpUrl()
            .newBuilder()
            .addQueryParameter("words", cleaned)
            .addQueryParameter("key", apiKey)
            .build()

        val request = Request.Builder().url(url).get().build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val parsedError = runCatching { json.decodeFromString<ConvertResponse>(body) }.getOrNull()
                    return@withContext What3WordsResult.Error(
                        parsedError?.error?.message ?: "what3words lookup failed (HTTP ${response.code})"
                    )
                }
                val parsed = json.decodeFromString<ConvertResponse>(body)
                val coords = parsed.coordinates
                if (coords != null) {
                    What3WordsResult.Success(LatLng(coords.lat, coords.lng))
                } else {
                    What3WordsResult.Error(parsed.error?.message ?: "Unrecognized what3words address")
                }
            }
        } catch (e: IOException) {
            What3WordsResult.Error(e.message ?: "Network error contacting what3words")
        }
    }
}
