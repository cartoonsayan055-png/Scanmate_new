package com.synthbyte.scanmate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Document::class, Page::class, QrHistory::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun docDao(): DocDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE documents ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS qr_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        value TEXT NOT NULL,
                        type TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL("UPDATE documents SET updatedAt = timestamp WHERE updatedAt = 0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN category TEXT NOT NULL DEFAULT 'General'")
                database.execSQL("ALTER TABLE documents ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE documents ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE documents ADD COLUMN workspace TEXT NOT NULL DEFAULT 'Inbox'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "scanmate_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
