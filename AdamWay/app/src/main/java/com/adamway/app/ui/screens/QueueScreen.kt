package com.adamway.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.adamway.app.data.AddressRepository
import com.adamway.app.data.SavedAddress
import com.adamway.app.ui.components.AddressCard
import com.adamway.app.ui.components.ConfirmDialog
import com.adamway.app.viewmodel.AddressViewModel

@Composable
fun QueueScreen(
    addressViewModel: AddressViewModel,
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onEditAddress: () -> Unit,
    onOpenNotes: (Long) -> Unit,
) {
    val context = LocalContext.current
    val queue by addressViewModel.queue.collectAsState()
    val favorites by addressViewModel.favorites.collectAsState()
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        ConfirmDialog(
            title = "Clear entire queue?",
            message = "This removes all ${queue.size} queued addresses. This can't be undone.",
            confirmLabel = "Clear queue",
            onConfirm = {
                addressViewModel.clearAll()
                showClearConfirm = false
            },
            onDismiss = { showClearConfirm = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Queue (${queue.size}/${AddressRepository.MAX_QUEUE_SIZE})", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (queue.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear queue", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                addressViewModel.startEditing(null)
                onAddAddress()
            }) {
                Icon(Icons.Filled.Add, contentDescription = "Add address")
            }
        },
    ) { padding ->
        fun quickAdd(favorite: SavedAddress) {
            if (queue.size >= AddressRepository.MAX_QUEUE_SIZE) {
                Toast.makeText(
                    context,
                    "Queue is full (${AddressRepository.MAX_QUEUE_SIZE} max) — remove a stop first",
                    Toast.LENGTH_LONG,
                ).show()
            } else {
                addressViewModel.quickAdd(favorite.addressText, favorite.what3words)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (favorites.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favorites.size) { i ->
                        val favorite = favorites[i]
                        AssistChip(
                            onClick = { quickAdd(favorite) },
                            label = { Text(favorite.favoriteLabel.orEmpty()) },
                            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                        )
                    }
                }
            }

            if (queue.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No addresses yet.\nTap + to add your first stop.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(queue.size) { index ->
                        val address = queue[index]
                        AddressCard(
                            index = index,
                            address = address,
                            canMoveUp = index > 0,
                            canMoveDown = index < queue.size - 1,
                            onClick = {
                                addressViewModel.startEditing(address)
                                onEditAddress()
                            },
                            onInfo = { address.savedAddressId?.let(onOpenNotes) },
                            onMoveUp = { addressViewModel.reorder(index, index - 1) },
                            onMoveDown = { addressViewModel.reorder(index, index + 1) },
                            onDelete = { addressViewModel.deleteAddress(address) },
                        )
                    }
                }
            }
        }
    }
}
