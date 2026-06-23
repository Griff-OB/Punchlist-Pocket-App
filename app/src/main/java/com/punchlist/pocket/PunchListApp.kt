package com.punchlist.pocket

import android.app.Application
import com.punchlist.pocket.data.local.AppDatabase
import com.punchlist.pocket.data.repository.AppRepository
import com.punchlist.pocket.data.seed.TemplateSeeder
import com.punchlist.pocket.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PunchListApp : Application() {

    /**
     * Application-scoped coroutine scope for background bookkeeping that isn't
     * tied to any screen — currently just the first-launch template seed.
     * Uses a [SupervisorJob] so one failure doesn't cancel siblings.
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy {
        AppRepository(
            database.jobDao(),
            database.punchItemDao(),
            database.photoDao(),
            database.templateDao(),
            database.templateItemDao()
        )
    }

    /** Persists user preferences (theme mode, etc.) via DataStore. */
    val settingsRepository by lazy { SettingsRepository(this) }

    override fun onCreate() {
        super.onCreate()
        // Seed the bundled starter templates on first launch (no-op once the
        // table is non-empty). Fire-and-forget; the count guard makes it safe.
        appScope.launch { TemplateSeeder.seedIfEmpty(repository) }
    }
}
