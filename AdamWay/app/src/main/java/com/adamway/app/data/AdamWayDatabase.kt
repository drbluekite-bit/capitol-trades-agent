package com.adamway.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Address::class], version = 1, exportSchema = false)
abstract class AdamWayDatabase : RoomDatabase() {
    abstract fun addressDao(): AddressDao

    companion object {
        @Volatile
        private var instance: AdamWayDatabase? = null

        fun get(context: Context): AdamWayDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdamWayDatabase::class.java,
                    "adam-way.db",
                ).build().also { instance = it }
            }
    }
}
