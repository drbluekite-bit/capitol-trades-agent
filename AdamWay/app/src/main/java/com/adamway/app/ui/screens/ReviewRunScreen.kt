package com.adamway.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adamway.app.viewmodel.DeliveryRunRow
import com.adamway.app.viewmodel.DeliveryRunViewModel
import kotlinx.coroutines.launch

/**
 * The scanned run so far, highest delivery number first (the real drop
 * order), each row editable and removable before anything is added to the
 * queue. [DeliverySheetParser][com.adamway.app.ocr.DeliverySheetParser]'s
 * guesses are exactly that — guesses — so this screen is the actual safety
 * net, not a formality.
 */
@Composable
fun ReviewRunScreen(
    deliveryRunViewModel: DeliveryRunViewModel,
    onScanMore: () -> Unit,
    onConfirmed: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rows by deliveryRunViewModel.rows.collectAsState()
    val sorted = rows.sortedByDescending { it.number ?: Int.MIN_VALUE }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Review run (${rows.size})", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Highest delivery number first, lowest last — check each one, fix anything misread, then confirm.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(16.dp),
            )

            if (rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No deliveries scanned yet.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(sorted.size) { i ->
                        val row = sorted[i]
                        DeliveryRunRowCard(
                            row = row,
                            onChange = { updated -> deliveryRunViewModel.updateRow(row.id, updated) },
                            onDelete = { deliveryRunViewModel.removeRow(row.id) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(onClick = onScanMore, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan more sheets")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch {
                            when (val result = deliveryRunViewModel.commit()) {
                                is DeliveryRunViewModel.CommitResult.Success ->
                                    Toast.makeText(context, "Added ${result.added} to the queue", Toast.LENGTH_LONG).show()
                                is DeliveryRunViewModel.CommitResult.PartiallyAdded ->
                                    Toast.makeText(
                                        context,
                                        "Added ${result.added} — ${result.skipped} skipped (queue full or missing an address)",
                                        Toast.LENGTH_LONG,
                                    ).show()
                            }
                            onConfirmed()
                        }
                    },
                    enabled = rows.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Confirm — add ${rows.size} to queue")
                }
            }
        }
    }
}

@Composable
private fun DeliveryRunRowCard(
    row: DeliveryRunRow,
    onChange: (DeliveryRunRow) -> Unit,
    onDelete: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = row.number?.toString().orEmpty(),
                    onValueChange = { text -> onChange(row.copy(number = text.filter { it.isDigit() }.toIntOrNull())) },
                    label = { Text("Delivery #") },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = row.addressText,
                onValueChange = { onChange(row.copy(addressText = it)) },
                label = { Text("Address") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = row.notes,
                onValueChange = { onChange(row.copy(notes = it)) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
