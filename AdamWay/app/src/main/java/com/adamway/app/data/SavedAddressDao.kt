package com.adamway.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedAddressDao {

    @Query("SELECT * FROM saved_addresses ORDER BY lastUsedAt DESC")
    fun observeAll(): Flow<List<SavedAddress>>

    @Query("SELECT * FROM saved_addresses")
    suspend fun getAll(): List<SavedAddress>

    @Query("SELECT * FROM saved_addresses WHERE id = :id")
    suspend fun getById(id: Long): SavedAddress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(address: SavedAddress): Long
}
