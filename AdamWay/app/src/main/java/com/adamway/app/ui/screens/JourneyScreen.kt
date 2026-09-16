package com.adamway.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adamway.app.viewmodel.JourneyUiState
import com.adamway.app.viewmodel.JourneyViewModel

@Composable
fun JourneyScreen(
    journeyViewModel: JourneyViewModel,
    autoBegin: Boolean,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val state by journeyViewModel.uiState.collectAsState()

    LaunchedEffect(autoBegin) {
        if (autoBegin) journeyViewModel.beginJourney(context)
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            when (val current = state) {
                is JourneyUiState.Idle -> {
                    Text("No active journey.", color = MaterialTheme.colorScheme.onBackground)
                }
                is JourneyUiState.Resolving -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Working out your route…", color = MaterialTheme.colorScheme.onBackground)
                    }
                }
                is JourneyUiState.Active -> {
                    val window = current.window
                    val nextStop = window.stops.first()
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            "Stop ${window.startIndex + 1} of ${current.totalStops}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            nextStop.displayLabel,
                            style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        nextStop.subLabel?.let {
                            Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                        }

                        if (current.warnings.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            current.warnings.forEach { warning ->
                                Text("⚠️ $warning", color = MaterialTheme.colorScheme.error)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Loaded in Google Maps: ${window.stops.size} stop${if (window.stops.size == 1) "" else "s"}",
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(window.stops.size) { i ->
                                val stop = window.stops[i]
                                Text(
                                    "${window.startIndex + i + 1}. ${stop.displayLabel}",
                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = if (i == 0) 1f else 0.6f),
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { journeyViewModel.openCurrentStopInWaze(context) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Open next stop in Waze") }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { journeyViewModel.arrivedNextStop(context) },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.onBackground,
                                contentColor = MaterialTheme.colorScheme.background,
                            ),
                        ) {
                            Text(
                                if (window.isFinalStop) "Arrived — Finish Journey" else "Arrived — Next Stop",
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        TextButton(onClick = { journeyViewModel.endJourney(); onFinished() }, modifier = Modifier.fillMaxWidth()) {
                            Text("End journey")
                        }
                    }
                }
                is JourneyUiState.Finished -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Journey complete 🎉", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { journeyViewModel.endJourney(); onFinished() }) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
