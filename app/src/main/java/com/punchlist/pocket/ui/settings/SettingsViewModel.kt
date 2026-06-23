package com.punchlist.pocket.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.punchlist.pocket.PunchListApp
import com.punchlist.pocket.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes user-controlled settings to the UI: the appearance (theme) preference
 * and the first-launch onboarding flag.
 */
class SettingsViewModel(app: PunchListApp) : ViewModel() {

    private val settings = app.settingsRepository

    val themeMode: StateFlow<ThemeMode> = settings.themeMode
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )

    /**
     * Whether the tutorial has been completed. Initial value is `false` so the
     * overlay can show immediately on a fresh launch before DataStore loads.
     */
    val onboardingCompleted: StateFlow<Boolean> = settings.onboardingCompleted
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    /** Marks the tutorial as finished so it won't show again on next launch. */
    fun completeOnboarding() {
        viewModelScope.launch { settings.setOnboardingCompleted(true) }
    }

    companion object {
        fun factory(app: PunchListApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(app) as T
                }
            }
    }
}
