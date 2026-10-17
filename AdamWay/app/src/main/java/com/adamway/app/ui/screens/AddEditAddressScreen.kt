package com.adamway.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adamway.app.data.SavedAddress
import com.adamway.app.viewmodel.AddressEvent
import com.adamway.app.viewmodel.AddressViewModel
import kotlinx.coroutines.delay

@Composable
fun AddEditAddressScreen(
    addressViewModel: AddressViewModel,
    onDone: () -> Unit,
) {
    val editing by addressViewModel.editingAddress.collectAsState()
    val context = LocalContext.current

    var addressText by rememberSaveable(editing) { mutableStateOf(editing?.addressText ?: "") }
    var what3words by rememberSaveable(editing) { mutableStateOf(editing?.what3words ?: "") }
    var selectedSavedAddressId by remember(editing) { mutableStateOf<Long?>(null) }
    var suggestions by remember { mutableStateOf<List<SavedAddress>>(emptyList()) }

    // A live, Google-Maps-search-style dropdown of candidate address lines
    // as you type — text only (see NominatimClient's doc): picking one just
    // fills the field with a complete, correctly-formatted line (postcode
    // included), it never sets a coordinate Adam Way would trust itself.
    var mapSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSearchingAddress by remember { mutableStateOf(false) }
    var suppressMapSuggestions by remember(editing) { mutableStateOf(false) }

    var showCamera by remember { mutableStateOf(false) }

    fun applySuggestion(suggestion: SavedAddress) {
        addressText = suggestion.addressText
        what3words = suggestion.what3words
        selectedSavedAddressId = suggestion.id
        suggestions = emptyList()
        mapSuggestions = emptyList()
        suppressMapSuggestions = true
    }

    fun applyMapSuggestion(suggestion: String) {
        addressText = suggestion
        selectedSavedAddressId = null
        suggestions = emptyList()
        mapSuggestions = emptyList()
        suppressMapSuggestions = true
    }

    val suggestionQuery = listOf(addressText, what3words).filter { it.isNotBlank() }.joinToString(" ")
    LaunchedEffect(suggestionQuery, editing) {
        suggestions = if (editing == null && selectedSavedAddressId == null && suggestionQuery.isNotBlank()) {
            addressViewModel.suggestAddresses(suggestionQuery)
        } else {
            emptyList()
        }
    }

    LaunchedEffect(addressText, suppressMapSuggestions) {
        if (suppressMapSuggestions || addressText.trim().length < 5) {
            mapSuggestions = emptyList()
            isSearchingAddress = false
        } else {
            isSearchingAddress = true
            delay(600)
            mapSuggestions = addressViewModel.searchAddressSuggestions(addressText)
            isSearchingAddress = false
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) showCamera = true
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (spoken.isNullOrBlank()) {
            Toast.makeText(context, "Didn't catch that — try again", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        addressText = spoken
        selectedSavedAddressId = null
    }

    fun launchVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak the address")
        }
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No speech recognizer app found on this device", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        addressViewModel.events.collect { event ->
            when (event) {
                AddressEvent.Added -> onDone()
                AddressEvent.QueueFull -> Toast.makeText(
                    context,
                    "Queue is full (${addressViewModel.maxQueueSize} max) — remove a stop first",
                    Toast.LENGTH_LONG,
                ).show()
                AddressEvent.BlankAddress -> Toast.makeText(
                    context,
                    "Enter an address before saving",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onTextRecognized = { parsed ->
                if (parsed.isNotBlank()) {
                    addressText = parsed
                    selectedSavedAddressId = null
                }
                showCamera = false
            },
            onCancel = { showCamera = false },
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (editing == null) "Add address" else "Edit address", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            Text(
                "Type the address exactly as you would into Google Maps' search bar — Maps or Waze will resolve it themselves when you start the journey. A what3words address is optional, as an alternative or extra.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )

            OutlinedButton(
                onClick = {
                    if (hasCameraPermission) showCamera = true else permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("  Scan with camera")
            }

            OutlinedButton(
                onClick = { launchVoiceInput() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null)
                Text("  Speak address")
            }

            OutlinedTextField(
                value = addressText,
                onValueChange = { addressText = it; selectedSavedAddressId = null; suppressMapSuggestions = false },
                label = { Text("Address") },
                placeholder = { Text("e.g. 24 Sandybrook Close, Bury BL8 3LB") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = what3words,
                onValueChange = { what3words = it; selectedSavedAddressId = null },
                label = { Text("what3words (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("///") },
            )

            if (isSearchingAddress) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
            }

            if (mapSuggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Matching addresses — tap the exact one:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    mapSuggestions.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { applyMapSuggestion(suggestion) }
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(suggestion, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            if (suggestions.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "From memory:",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                    suggestions.forEach { suggestion ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .clickable { applySuggestion(suggestion) }
                                .padding(12.dp),
                        ) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(suggestion.displayLabel, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val current = editing
                    if (current == null) {
                        addressViewModel.addAddress(addressText, what3words, selectedSavedAddressId)
                    } else {
                        addressViewModel.updateAddress(
                            current.copy(
                                addressText = addressText.trim(),
                                what3words = what3words.trim().removePrefix("///"),
                            ),
                        )
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing == null) "Add to queue" else "Save changes")
            }
        }
    }
}
