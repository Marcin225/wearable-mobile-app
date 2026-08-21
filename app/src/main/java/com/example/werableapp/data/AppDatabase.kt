package com.example.werableapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Local database for storing wearable data
 */
@Database(entities = [WearableData::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wearableDao(): WearableDao

    companion object {
        // keep only one database instance in the whole app
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wearable_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}