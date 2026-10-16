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
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adamway.app.data.SavedAddress
import com.adamway.app.location.AddressSuggestion
import com.adamway.app.ocr.AddressParser
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

    var houseNumber by rememberSaveable(editing) { mutableStateOf(editing?.houseNumber ?: "") }
    var houseName by rememberSaveable(editing) { mutableStateOf(editing?.houseName ?: "") }
    var postcode by rememberSaveable(editing) { mutableStateOf(editing?.postcode ?: "") }
    var what3words by rememberSaveable(editing) { mutableStateOf(editing?.what3words ?: "") }
    var selectedSavedAddressId by remember(editing) { mutableStateOf<Long?>(null) }
    var suggestions by remember { mutableStateOf<List<SavedAddress>>(emptyList()) }

    // Set once the user taps a result from the live address search below —
    // this is what actually gets sent to Maps at journey time, instead of
    // Adam Way geocoding the typed fields itself and hoping it guessed
    // right.
    var confirmedLat by remember(editing) { mutableStateOf<Double?>(null) }
    var confirmedLng by remember(editing) { mutableStateOf<Double?>(null) }
    var mapSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var isSearchingMap by remember { mutableStateOf(false) }

    var showCamera by remember { mutableStateOf(false) }

    fun clearConfirmedPick() {
        selectedSavedAddressId = null
        confirmedLat = null
        confirmedLng = null
    }

    fun applySuggestion(suggestion: SavedAddress) {
        houseNumber = suggestion.houseNumber
        houseName = suggestion.houseName
        postcode = suggestion.postcode
        what3words = suggestion.what3words
        selectedSavedAddressId = suggestion.id
        confirmedLat = suggestion.resolvedLat
        confirmedLng = suggestion.resolvedLng
        suggestions = emptyList()
        mapSuggestions = emptyList()
    }

    fun applyMapSuggestion(suggestion: AddressSuggestion) {
        if (suggestion.houseNumber.isNotBlank()) houseNumber = suggestion.houseNumber
        if (suggestion.road.isNotBlank()) houseName = suggestion.road
        if (suggestion.postcode.isNotBlank()) postcode = suggestion.postcode
        selectedSavedAddressId = null
        confirmedLat = suggestion.location.lat
        confirmedLng = suggestion.location.lng
        mapSuggestions = emptyList()
        suggestions = emptyList()
    }

    val suggestionQuery = listOf(houseNumber, houseName, postcode, what3words).filter { it.isNotBlank() }.joinToString(" ")
    LaunchedEffect(suggestionQuery, editing) {
        suggestions = if (editing == null && selectedSavedAddressId == null && suggestionQuery.isNotBlank()) {
            addressViewModel.suggestAddresses(suggestionQuery)
        } else {
            emptyList()
        }
    }

    // A live, Google-Maps-search-style lookup: as you type a number and
    // street, this shows real candidate addresses to pick the exact one
    // from — debounced so it only fires once you pause typing, and only
    // once there's enough to search on.
    val mapQuery = listOf(houseNumber, houseName, postcode).filter { it.isNotBlank() }.joinToString(" ")
    LaunchedEffect(mapQuery) {
        if (confirmedLat != null || mapQuery.trim().length < 5) {
            mapSuggestions = emptyList()
            isSearchingMap = false
        } else {
            isSearchingMap = true
            delay(600)
            mapSuggestions = addressViewModel.searchMapAddresses(mapQuery)
            isSearchingMap = false
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
        val parsed = AddressParser.parse(spoken)
        if (parsed.houseNumber.isNotBlank()) houseNumber = parsed.houseNumber
        if (parsed.houseName.isNotBlank()) houseName = parsed.houseName
        if (parsed.postcode.isNotBlank()) postcode = parsed.postcode
        if (parsed.houseNumber.isBlank() && parsed.houseName.isBlank() && parsed.postcode.isBlank()) {
            // Couldn't pick out a number/postcode — drop the raw transcript into house name
            // rather than lose it, so the user can still see and edit what was heard.
            houseName = spoken
        }
        clearConfirmedPick()
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
                    "Enter at least one field before saving",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    if (showCamera) {
        CameraCaptureScreen(
            onTextRecognized = { parsed ->
                if (parsed.houseNumber.isNotBlank()) houseNumber = parsed.houseNumber
                if (parsed.houseName.isNotBlank()) houseName = parsed.houseName
                if (parsed.postcode.isNotBlank()) postcode = parsed.postcode
                clearConfirmedPick()
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
                "Fill in whichever details you have — none of these fields are required on their own, but at least one is needed.",
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
                value = houseNumber,
                onValueChange = { houseNumber = it; clearConfirmedPick() },
                label = { Text("House number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = houseName,
                onValueChange = { houseName = it; clearConfirmedPick() },
                label = { Text("House name / street") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = postcode,
                onValueChange = { postcode = it; clearConfirmedPick() },
                label = { Text("Postcode") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = what3words,
                onValueChange = { what3words = it; clearConfirmedPick() },
                label = { Text("what3words (e.g. filled.count.soap)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("///") },
            )

            if (confirmedLat != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Exact location confirmed from the map",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }

            if (isSearchingMap) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Searching the map…", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    suggestion.displayName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        addressViewModel.addAddress(
                            houseNumber, houseName, postcode, what3words,
                            selectedSavedAddressId, confirmedLat, confirmedLng,
                        )
                    } else {
                        addressViewModel.updateAddress(
                            current.copy(
                                houseNumber = houseNumber.trim(),
                                houseName = houseName.trim(),
                                postcode = postcode.trim(),
                                what3words = what3words.trim().removePrefix("///"),
                            ),
                            confirmedLat,
                            confirmedLng,
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
