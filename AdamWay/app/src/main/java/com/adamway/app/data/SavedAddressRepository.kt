package com.adamway.app.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class SavedAddressRepository(private val dao: SavedAddressDao) {

    fun observeAll(): Flow<List<SavedAddress>> = dao.observeAll()

    suspend fun getById(id: Long): SavedAddress? = dao.getById(id)

    /** Up to 5 addresses from memory whose details loosely match [query], most recently used first. */
    suspend fun search(query: String): List<SavedAddress> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return dao.getAll()
            .filter { candidate ->
                listOf(candidate.houseNumber, candidate.houseName, candidate.postcode, candidate.what3words)
                    .any { it.isNotBlank() && it.lowercase().contains(needle) }
            }
            .sortedByDescending { it.lastUsedAt }
            .take(5)
    }

    /**
     * Finds the saved address matching these details, refreshing its
     * "last used" time, or creates a new one if this is the first time
     * this address has been added.
     */
    suspend fun findOrCreate(houseNumber: String, houseName: String, postcode: String, what3words: String): Long {
        val match = dao.getAll().firstOrNull { it.matches(houseNumber, houseName, postcode, what3words) }
        return if (match != null) {
            dao.upsert(match.copy(lastUsedAt = System.currentTimeMillis()))
            match.id
        } else {
            dao.upsert(
                SavedAddress(
                    houseNumber = houseNumber.trim(),
                    houseName = houseName.trim(),
                    postcode = postcode.trim(),
                    what3words = what3words.trim(),
                )
            )
        }
    }

    /** Keeps the address book's core fields in sync when a queued address is edited. */
    suspend fun syncCoreFields(id: Long, houseNumber: String, houseName: String, postcode: String, what3words: String) {
        val existing = dao.getById(id) ?: return
        dao.upsert(
            existing.copy(
                houseNumber = houseNumber.trim(),
                houseName = houseName.trim(),
                postcode = postcode.trim(),
                what3words = what3words.trim(),
            )
        )
    }

    suspend fun updateNotes(id: Long, notes: String, photoPaths: List<String>) {
        val existing = dao.getById(id) ?: return
        dao.upsert(existing.copy(notes = notes, notePhotoPaths = photoPaths.joinToString(SavedAddress.PHOTO_PATH_DELIMITER)))
    }

    fun deletePhotoFile(path: String) {
        runCatching { File(path).delete() }
    }
}
