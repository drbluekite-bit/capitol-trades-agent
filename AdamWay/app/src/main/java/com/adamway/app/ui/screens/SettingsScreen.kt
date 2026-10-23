package com.adamway.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.adamway.app.data.PreferredNavApp
import com.adamway.app.viewmodel.AddressViewModel
import com.adamway.app.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    settingsViewModel: SettingsViewModel,
    addressViewModel: AddressViewModel,
    onBack: () -> Unit,
) {
    val savedKey by settingsViewModel.what3wordsApiKey.collectAsState()
    val preferredNavApp by settingsViewModel.preferredNavApp.collectAsState()
    var apiKeyField by remember(savedKey) { mutableStateOf(savedKey) }
    var showClearConfirm by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("what3words", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Optional. Add your free what3words API key (from developer.what3words.com) to " +
                    "turn ///three.word.addresses into map coordinates so they can be included " +
                    "in the same Google Maps route as your other stops. Without a key, any " +
                    "what3words-only entries open directly in the What3Words app instead.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
            OutlinedTextField(
                value = apiKeyField,
                onValueChange = { apiKeyField = it },
                label = { Text("What3Words API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { settingsViewModel.setWhat3wordsApiKey(apiKeyField) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save key")
            }

            Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))

            Text("Preferred navigation app", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Row {
                RadioButton(
                    selected = preferredNavApp == PreferredNavApp.GOOGLE_MAPS,
                    onClick = { settingsViewModel.setPreferredNavApp(PreferredNavApp.GOOGLE_MAPS) },
                )
                Text(
                    "Google Maps (multi-stop journeys)",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            Row {
                RadioButton(
                    selected = preferredNavApp == PreferredNavApp.WAZE,
                    onClick = { settingsViewModel.setPreferredNavApp(PreferredNavApp.WAZE) },
                )
                Text(
                    "Waze (single stop only — used for the “Open in Waze” shortcut)",
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))

            Text("Privacy", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "Your addresses and queue never leave this phone — they're stored in a local " +
                    "database only. Photos taken for address scanning are processed on-device and " +
                    "are never saved or uploaded. The only network calls this app makes are: " +
                    "(1) an optional lookup of a what3words address at api.what3words.com using " +
                    "the key above, and (2) opening Google Maps, Waze or What3Words when you tap " +
                    "a navigation button, which hands your route off to that app directly.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))

            if (!showClearConfirm) {
                TextButton(onClick = { showClearConfirm = true }) {
                    Text("Clear entire queue", color = MaterialTheme.colorScheme.error)
                }
            } else {
                Text("Delete all queued addresses? This can't be undone.", color = MaterialTheme.colorScheme.error)
                Row {
                    TextButton(onClick = {
                        addressViewModel.clearAll()
                        showClearConfirm = false
                    }) { Text("Yes, clear it", color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
                }
            }
        }
    }
}
