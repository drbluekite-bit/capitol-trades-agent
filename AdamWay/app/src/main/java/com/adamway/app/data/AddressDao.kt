package com.adamway.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AddressDao {

    @Query("SELECT * FROM addresses ORDER BY position ASC")
    fun observeAll(): Flow<List<Address>>

    @Query("SELECT * FROM addresses ORDER BY position ASC")
    suspend fun getAll(): List<Address>

    @Query("SELECT COUNT(*) FROM addresses")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(address: Address): Long

    @Update
    suspend fun update(address: Address)

    @Update
    suspend fun updateAll(addresses: List<Address>)

    @Delete
    suspend fun delete(address: Address)

    @Query("DELETE FROM addresses")
    suspend fun deleteAll()
}
