package com.punchlist.pocket

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.punchlist.pocket.data.settings.ThemeMode
import com.punchlist.pocket.ui.navigation.AppNavigation
import com.punchlist.pocket.ui.settings.SettingsViewModel
import com.punchlist.pocket.ui.theme.PunchListTheme
import com.punchlist.pocket.utils.NotificationHelper

class MainActivity : ComponentActivity() {

    // Runtime request for the Android 13+ POST_NOTIFICATIONS permission. The
    // due-soon reminders silently no-op without it, so we ask once on launch;
    // a denial isn't fatal — notifications just won't show.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result is irrelevant: NotificationHelper guards on enabled state. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureNotificationPermission()
        enableEdgeToEdge()
        setContent {
            val settings: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory(application as PunchListApp)
            )
            // Resolve the stored choice: SYSTEM follows the device, the other
            // two force a palette. Material You is deliberately left off so the
            // strong blue branding stays consistent on Android 12+.
            val themeMode by settings.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            PunchListTheme(darkTheme = isDark, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }

    /**
     * Creates the notification channel (cheap, idempotent) and asks for
     * POST_NOTIFICATIONS on Android 13+ if it isn't already granted. Channel
     * creation happens here rather than in Application.onCreate so the helper's
     * lazy guard still applies, but it's ready before any screen can fire one.
     */
    private fun ensureNotificationPermission() {
        NotificationHelper.ensureChannel(applicationContext)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
