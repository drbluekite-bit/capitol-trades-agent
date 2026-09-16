package com.adamway.app.data

import kotlinx.coroutines.flow.Flow

class AddressRepository(private val dao: AddressDao) {

    companion object {
        /** Google Maps' own address-picker tops out at 10; this is the app's overall cap. */
        const val MAX_QUEUE_SIZE = 18
    }

    sealed class AddResult {
        data class Success(val id: Long) : AddResult()
        object QueueFull : AddResult()
        object BlankAddress : AddResult()
    }

    fun observeQueue(): Flow<List<Address>> = dao.observeAll()

    suspend fun addAddress(
        houseNumber: String,
        houseName: String,
        postcode: String,
        what3words: String,
    ): AddResult {
        val candidate = Address(
            houseNumber = houseNumber.trim(),
            houseName = houseName.trim(),
            postcode = postcode.trim(),
            what3words = normalizeWhat3Words(what3words),
            position = 0,
        )
        if (candidate.isBlank) return AddResult.BlankAddress

        val existing = dao.getAll()
        if (existing.size >= MAX_QUEUE_SIZE) return AddResult.QueueFull

        val id = dao.insert(candidate.copy(position = existing.size))
        return AddResult.Success(id)
    }

    suspend fun updateAddress(address: Address) {
        dao.update(address.copy(what3words = normalizeWhat3Words(address.what3words)))
    }

    suspend fun deleteAddress(address: Address) {
        dao.delete(address)
        renumber()
    }

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun markVisited(addressId: Long) {
        val all = dao.getAll()
        val target = all.firstOrNull { it.id == addressId } ?: return
        dao.update(target.copy(visited = true))
    }

    /** Moves the address at [fromIndex] to [toIndex] within the ordered queue. */
    suspend fun reorder(fromIndex: Int, toIndex: Int) {
        val all = dao.getAll().toMutableList()
        if (fromIndex !in all.indices || toIndex !in all.indices) return
        val moved = all.removeAt(fromIndex)
        all.add(toIndex, moved)
        dao.updateAll(all.mapIndexed { index, address -> address.copy(position = index) })
    }

    private suspend fun renumber() {
        val all = dao.getAll()
        dao.updateAll(all.mapIndexed { index, address -> address.copy(position = index) })
    }

    private fun normalizeWhat3Words(raw: String): String =
        raw.trim().removePrefix("///").lowercase()
}
