package com.adamway.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adamway.app.viewmodel.SavedAddressViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Notes and photos "retained against" an address — opened via the info
 * button on a queue row. Backed by [com.adamway.app.data.SavedAddress], so
 * what's added here is still there next time the same address comes up,
 * even after the queue itself has been cleared.
 */
@Composable
fun AddressNotesScreen(
    savedAddressViewModel: SavedAddressViewModel,
    savedAddressId: Long,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val address by savedAddressViewModel.address.collectAsState()
    var notes by remember(address?.id) { mutableStateOf(address?.notes ?: "") }

    LaunchedEffect(savedAddressId) { savedAddressViewModel.load(savedAddressId) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            val dir = File(context.filesDir, "note_photos").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
            savedAddressViewModel.addPhoto(file.absolutePath)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(address?.displayLabel ?: "Notes", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { savedAddressViewModel.saveNotes(notes) }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save notes", tint = MaterialTheme.colorScheme.onBackground)
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
            address?.postcode?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }

            Text("Notes", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text("Parking instructions, gate code, delivery notes…") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            Button(
                onClick = { savedAddressViewModel.saveNotes(notes) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save notes")
            }

            Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))

            Text("Photos", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
            OutlinedButton(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                Text("  Add photo")
            }

            val photos = address?.photoPathList.orEmpty()
            if (photos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(photos.size) { i ->
                        val path = photos[i]
                        Box {
                            NotePhotoThumbnail(path)
                            IconButton(
                                onClick = { savedAddressViewModel.removePhoto(path) },
                                modifier = Modifier.align(Alignment.TopEnd).size(28.dp),
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotePhotoThumbnail(path: String) {
    val bitmap = remember(path) { decodeSampledBitmap(path, 300)?.asImageBitmap() }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
    }
}

private fun decodeSampledBitmap(path: String, targetSize: Int): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / sample > targetSize || bounds.outHeight / sample > targetSize) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
} catch (e: Exception) {
    null
}
