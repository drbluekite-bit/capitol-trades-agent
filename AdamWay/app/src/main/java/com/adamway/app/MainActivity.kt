package com.adamway.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.adamway.app.ui.navigation.AdamWayNavHost
import com.adamway.app.ui.theme.AdamWayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleShareIntent(intent)
        setContent {
            AdamWayTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AdamWayNavHost()
                }
            }
        }
    }

    // launchMode="singleTask" (see the manifest) means a share arriving
    // while Adam Way is already running — the common case, since you'd
    // typically get here via this app's own "Search in Google Maps"
    // button — lands here instead of spinning up a second instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    /**
     * Lets you find a place in Google Maps, tap its own Share button, and
     * pick Adam Way from the share sheet to save it as a named favorite —
     * Maps' own search covers far more businesses/schools than the free
     * OpenStreetMap data this app's own address suggestions use.
     *
     * No MIME-type check here, deliberately: the manifest's own
     * `<data android:mimeType="text/plain" />` intent-filter is what gets
     * Adam Way into the share sheet in the first place, and a second,
     * stricter check here (an earlier version required `intent.type` to
     * equal or start with "text/plain" exactly) risked silently rejecting
     * a share Android had already decided was relevant — with nothing on
     * screen to show it even happened. The Toasts below are a temporary
     * diagnostic: this can't be tested from the authoring sandbox (no way
     * to simulate a real device's share sheet), so if a share still isn't
     * landing correctly, what these say pinpoints exactly why.
     */
    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND) return

        val shared = (intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT))
            ?.trim().orEmpty()
        if (shared.isBlank()) {
            Toast.makeText(this, "Adam Way got a share with no usable text (type: ${intent.type})", Toast.LENGTH_LONG).show()
            return
        }

        // Google Maps' share text is typically "Place Name\nhttps://maps.app.goo.gl/xxxx"
        // (sometimes just the link on its own) — take the first non-link
        // line as both the starting address text and the suggested
        // favorite name; fall back to the raw shared text if there isn't one.
        val firstLine = shared.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("http") }

        (application as AdamWayApplication).pendingShare.value =
            PendingShare(addressText = firstLine ?: shared, suggestedLabel = firstLine.orEmpty())
        Toast.makeText(this, "Adam Way: opening \"${firstLine ?: shared}\" as a new favorite…", Toast.LENGTH_SHORT).show()
    }
}
