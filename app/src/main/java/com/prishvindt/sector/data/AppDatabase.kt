package com.prishvindt.sector.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [SectorObjectEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sectorObjectDao(): SectorObjectDao

    companion object {
        private const val DATABASE_NAME = "sector.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS imported_locations (
                        location_key TEXT NOT NULL,
                        callsign TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy_m REAL,
                        timestamp INTEGER NOT NULL,
                        received_at INTEGER NOT NULL,
                        PRIMARY KEY(location_key)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS measurements")
                db.execSQL("DROP TABLE IF EXISTS imported_locations")
                createSectorObjectsTable(db)
            }
        }

        fun get(context: Context): AppDatabase {
            deleteIncompatibleVersion3DatabaseIfNeeded(context)
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }

        private fun createSectorObjectsTable(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sector_objects (
                    object_id TEXT NOT NULL,
                    object_type TEXT NOT NULL,
                    owner_kind TEXT NOT NULL,
                    owner_id TEXT,
                    device_id TEXT,
                    source_kind TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    deleted_at INTEGER,
                    sync_state TEXT NOT NULL,
                    visibility TEXT NOT NULL,
                    encryption_state TEXT NOT NULL,
                    payload_version INTEGER NOT NULL,
                    payload_json TEXT NOT NULL,
                    PRIMARY KEY(object_id)
                )
                """.trimIndent()
            )
        }

        private fun deleteIncompatibleVersion3DatabaseIfNeeded(context: Context) {
            val dbFile = context.getDatabasePath(DATABASE_NAME)
            if (!dbFile.exists()) return

            val shouldDelete = runCatching {
                SQLiteDatabase.openDatabase(
                    dbFile.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.version == 3 && !db.hasTable("sector_objects")
                }
            }.getOrDefault(false)

            if (shouldDelete) {
                context.deleteDatabase(DATABASE_NAME)
            }
        }

        private fun SQLiteDatabase.hasTable(tableName: String): Boolean =
            rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                arrayOf(tableName)
            ).use { cursor -> cursor.moveToFirst() }
    }
}
