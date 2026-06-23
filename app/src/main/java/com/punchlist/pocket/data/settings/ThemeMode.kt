package com.punchlist.pocket.data.settings

/**
 * The user's appearance preference. `SYSTEM` defers to the device's current
 * dark/light setting; the other two force the palette regardless of system.
 */
enum class ThemeMode(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System")
}
