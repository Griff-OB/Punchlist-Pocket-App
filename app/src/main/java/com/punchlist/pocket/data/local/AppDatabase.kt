package com.punchlist.pocket.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Job::class,
        PunchItem::class,
        Photo::class,
        Template::class,
        TemplateItem::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun jobDao(): JobDao
    abstract fun punchItemDao(): PunchItemDao
    abstract fun photoDao(): PhotoDao
    abstract fun templateDao(): TemplateDao
    abstract fun templateItemDao(): TemplateItemDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * No-op migration from v1 → v2. The `dueDate` column already existed
         * on [PunchItem] in v1; v2 only bumps the schema version to match the
         * now-formally-declared entity set, so no DDL change is required.
         * Keeping an explicit (empty) migration avoids wiping user data that
         * `fallbackToDestructiveMigration` would otherwise destroy.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Intentionally empty — no schema change.
            }
        }

        /**
         * v2 → v3: adds the nullable `imagePath` column to `jobs` so users can
         * optionally attach a cover image to a project. Existing rows get NULL
         * (no image), which the Home card renders as a letter avatar fallback.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE jobs ADD COLUMN imagePath TEXT")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "punchlist.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
