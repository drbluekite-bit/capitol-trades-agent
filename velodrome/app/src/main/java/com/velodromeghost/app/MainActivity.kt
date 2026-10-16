package com.velodromeghost.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.velodromeghost.app.ui.RaceScreen
import com.velodromeghost.app.ui.ResultsScreen
import com.velodromeghost.app.ui.SetupScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // You're on the bike - don't let the screen sleep.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            VelodromeTheme {
                Box(Modifier.fillMaxSize().background(Palette.background)) {
                    App()
                }
            }
        }
    }
}

@Composable
private fun App(vm: RaceViewModel = viewModel()) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    when (screen) {
        Screen.SETUP -> SetupScreen(vm)
        Screen.RACE -> RaceScreen(vm)
        Screen.RESULTS -> ResultsScreen(vm)
    }
}

object Palette {
    val background = Color(0xFF0B1020)
    val surface = Color(0xFF151C33)
    val rider = Color(0xFFFF9800)
    val ghost = Color(0xFF8BEAFF)
    val good = Color(0xFF4CAF50)
    val bad = Color(0xFFE53935)
    val accent = Color(0xFFFFC107)
    val muted = Color(0xFF9AA4C0)
}

@Composable
fun VelodromeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.rider,
            onPrimary = Color.Black,
            secondary = Palette.ghost,
            onSecondary = Color.Black,
            background = Palette.background,
            surface = Palette.surface,
            surfaceVariant = Color(0xFF1E2744),
            onSurface = Color(0xFFE8ECF8),
            onSurfaceVariant = Palette.muted,
        ),
        content = content,
    )
}
