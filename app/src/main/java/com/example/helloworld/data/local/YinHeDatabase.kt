package com.example.helloworld.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [PlaylistEntity::class, FavoriteTrackEntity::class, CachedTrackEntity::class, PlaylistTrackEntity::class, FriendEntity::class, ImMessageEntity::class, RecognizeHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class YinHeDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun favoriteTrackDao(): FavoriteTrackDao
    abstract fun cachedTrackDao(): CachedTrackDao
    abstract fun playlistTrackDao(): PlaylistTrackDao
    abstract fun friendDao(): FriendDao
    abstract fun imMessageDao(): ImMessageDao
    abstract fun recognizeHistoryDao(): RecognizeHistoryDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_tracks` (
                        `trackId` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT,
                        `durationSeconds` INTEGER,
                        `coverUrl` TEXT,
                        `source` TEXT NOT NULL,
                        `apiTrackJson` TEXT NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_tracks` (
                        `trackId` TEXT NOT NULL PRIMARY KEY,
                        `title` TEXT NOT NULL,
                        `artist` TEXT NOT NULL,
                        `album` TEXT,
                        `durationSeconds` INTEGER,
                        `coverUrl` TEXT,
                        `source` TEXT NOT NULL,
                        `streamUrl` TEXT NOT NULL,
                        `apiTrackJson` TEXT NOT NULL,
                        `totalBytes` INTEGER NOT NULL,
                        `cachedBytes` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                        `playlistId` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `trackId` TEXT NOT NULL,
                        `trackJson` TEXT NOT NULL,
                        PRIMARY KEY(`playlistId`, `position`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `friends` (
                        `username` TEXT NOT NULL PRIMARY KEY,
                        `since` INTEGER NOT NULL,
                        `online` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `im_messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `peer` TEXT NOT NULL,
                        `outgoing` INTEGER NOT NULL,
                        `msgType` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `trackJson` TEXT NOT NULL,
                        `ts` INTEGER NOT NULL,
                        `read` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recognize_history` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `ts` INTEGER NOT NULL,
                        `trackCount` INTEGER NOT NULL,
                        `tracksJson` TEXT NOT NULL,
                        `note` TEXT NOT NULL DEFAULT ''
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var instance: YinHeDatabase? = null
        fun getInstance(context: Context): YinHeDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    YinHeDatabase::class.java,
                    "yinhe.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
            }
        }
    }
}
