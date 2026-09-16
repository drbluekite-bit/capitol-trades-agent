package com.adamway.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.adamway.app.data.AddressRepository
import com.adamway.app.ui.components.AddressCard
import com.adamway.app.viewmodel.AddressViewModel

@Composable
fun QueueScreen(
    addressViewModel: AddressViewModel,
    onBack: () -> Unit,
    onAddAddress: () -> Unit,
    onEditAddress: () -> Unit,
) {
    val queue by addressViewModel.queue.collectAsState()

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
        if (queue.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No addresses yet.\nTap + to add your first stop.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
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
                        onMoveUp = { addressViewModel.reorder(index, index - 1) },
                        onMoveDown = { addressViewModel.reorder(index, index + 1) },
                        onDelete = { addressViewModel.deleteAddress(address) },
                    )
                }
            }
        }
    }
}
