package com.adamway.app

import android.content.Intent
import android.os.Bundle
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
     */
    private fun handleShareIntent(intent: Intent) {
        if (intent.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (shared.isBlank()) return

        // Google Maps' share text is typically "Place Name\nhttps://maps.app.goo.gl/xxxx"
        // (sometimes just the link on its own) — take the first non-link
        // line as both the starting address text and the suggested
        // favorite name; fall back to the raw shared text if there isn't one.
        val firstLine = shared.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && !it.startsWith("http") }

        (application as AdamWayApplication).pendingShare.value =
            PendingShare(addressText = firstLine ?: shared, suggestedLabel = firstLine.orEmpty())
    }
}
