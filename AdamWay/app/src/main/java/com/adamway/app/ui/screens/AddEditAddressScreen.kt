package com.adamway.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.adamway.app.viewmodel.AddressEvent
import com.adamway.app.viewmodel.AddressViewModel

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
    var showCamera by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) showCamera = true
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

            OutlinedTextField(
                value = houseNumber,
                onValueChange = { houseNumber = it },
                label = { Text("House number") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = houseName,
                onValueChange = { houseName = it },
                label = { Text("House name / street") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = postcode,
                onValueChange = { postcode = it },
                label = { Text("Postcode") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = what3words,
                onValueChange = { what3words = it },
                label = { Text("what3words (e.g. filled.count.soap)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                prefix = { Text("///") },
            )

            Button(
                onClick = {
                    val current = editing
                    if (current == null) {
                        addressViewModel.addAddress(houseNumber, houseName, postcode, what3words)
                    } else {
                        addressViewModel.updateAddress(
                            current.copy(
                                houseNumber = houseNumber.trim(),
                                houseName = houseName.trim(),
                                postcode = postcode.trim(),
                                what3words = what3words.trim().removePrefix("///"),
                            )
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
