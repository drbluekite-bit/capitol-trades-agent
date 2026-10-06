package com.adamway.app.maps

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** A single leg of a Maps route: either a resolved point or a free-text search. */
sealed class MapsStop {
    data class Coordinate(val lat: Double, val lng: Double) : MapsStop()
    data class Text(val query: String) : MapsStop()
}

/**
 * Builds and fires the Android intents that hand a leg of the journey off
 * to Google Maps, Waze, or What3Words. No Maps/Directions API key is used —
 * these are plain deep links, so launching a leg costs nothing and needs no
 * network call of the app's own.
 */
object MapsLauncher {

    /**
     * Opens Google Maps turn-by-turn directions for up to 10 stops in one go
     * (this app never asks for more than that per leg): [stops].last() is the
     * destination, everything before it becomes a waypoint. Origin is left
     * out so Maps starts from the phone's current location.
     */
    fun openGoogleMapsRoute(context: Context, stops: List<MapsStop>) {
        require(stops.isNotEmpty()) { "Need at least one stop" }
        val destination = encodeStop(stops.last())
        val waypoints = stops.dropLast(1)

        val builder = StringBuilder("https://www.google.com/maps/dir/?api=1")
        builder.append("&destination=").append(destination)
        if (waypoints.isNotEmpty()) {
            val encodedWaypoints = waypoints.joinToString("|") { encodeStop(it) }
            builder.append("&waypoints=").append(encodedWaypoints)
        }
        builder.append("&travelmode=driving")

        launch(context, builder.toString(), "com.google.android.apps.maps")
    }

    /**
     * Google's documented Maps URL format expects a literal, unencoded
     * comma for a "lat,lng" coordinate pair — percent-encoding it to %2C
     * (which a blanket Uri.encode() would do) makes the Maps app reject
     * the link outright ("This type of link isn't supported"). Free-text
     * addresses, on the other hand, need full encoding for spaces, commas
     * within the address itself, etc.
     */
    private fun encodeStop(stop: MapsStop): String = when (stop) {
        is MapsStop.Coordinate -> "${stop.lat},${stop.lng}"
        is MapsStop.Text -> Uri.encode(stop.query)
    }

    /** Waze only supports a single destination per link, so this is used for the next stop only. */
    fun openWaze(context: Context, stop: String) {
        val uri = "https://waze.com/ul?q=${Uri.encode(stop)}&navigate=yes"
        launch(context, uri, "com.waze")
    }

    fun openWazeCoordinates(context: Context, lat: Double, lng: Double) {
        val uri = "https://waze.com/ul?ll=$lat,$lng&navigate=yes"
        launch(context, uri, "com.waze")
    }

    /** Opens the What3Words app directly on a three-word address, e.g. when it can't be resolved to coordinates. */
    fun openWhat3Words(context: Context, words: String) {
        val cleaned = words.trim().removePrefix("///")
        val uri = "https://w3w.co/$cleaned"
        launch(context, uri, "com.what3words.android")
    }

    private fun launch(context: Context, uri: String, preferredPackage: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        val targeted = Intent(intent).setPackage(preferredPackage)
        try {
            context.startActivity(targeted)
        } catch (e: ActivityNotFoundException) {
            try {
                context.startActivity(intent)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(context, "No app found to handle this link", Toast.LENGTH_LONG).show()
            }
        }
    }
}
