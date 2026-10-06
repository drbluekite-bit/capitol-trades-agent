package com.adamway.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Address::class, SavedAddress::class], version = 2, exportSchema = false)
abstract class AdamWayDatabase : RoomDatabase() {
    abstract fun addressDao(): AddressDao
    abstract fun savedAddressDao(): SavedAddressDao

    companion object {
        @Volatile
        private var instance: AdamWayDatabase? = null

        fun get(context: Context): AdamWayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdamWayDatabase::class.java,
                    "adam-way.db",
                )
                    // This app has no release users yet, so a schema bump just
                    // resets local data rather than needing a real migration.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
