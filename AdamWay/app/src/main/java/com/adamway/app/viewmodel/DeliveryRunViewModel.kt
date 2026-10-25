package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import com.adamway.app.data.AddressRepository
import com.adamway.app.data.SavedAddressRepository
import com.adamway.app.ocr.ParsedDelivery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One row of an in-progress scanned delivery run, before it's added to the queue. */
data class DeliveryRunRow(
    val id: Long,
    val number: Int?,
    val addressText: String,
    val notes: String,
)

/**
 * Holds the delivery run being built up across a multi-photo scan session
 * (see [com.adamway.app.ocr.DeliverySheetParser]) — rows accumulate here as
 * each sheet is photographed, can be edited or removed on the review
 * screen, and only touch the real queue once [commit] is called.
 */
class DeliveryRunViewModel(
    private val addressRepository: AddressRepository,
    private val savedAddressRepository: SavedAddressRepository,
) : ViewModel() {

    private val _rows = MutableStateFlow<List<DeliveryRunRow>>(emptyList())
    val rows: StateFlow<List<DeliveryRunRow>> = _rows.asStateFlow()

    private val _photosScanned = MutableStateFlow(0)
    val photosScanned: StateFlow<Int> = _photosScanned.asStateFlow()

    private var nextId = 0L

    /** Appends whatever was found on one freshly-photographed sheet to the run so far. */
    fun addParsedFromPhoto(parsed: List<ParsedDelivery>) {
        val withIds = parsed.map { DeliveryRunRow(id = nextId++, number = it.number, addressText = it.addressText, notes = it.notes) }
        _rows.value = _rows.value + withIds
        _photosScanned.value += 1
    }

    fun updateRow(id: Long, updated: DeliveryRunRow) {
        _rows.value = _rows.value.map { if (it.id == id) updated else it }
    }

    fun removeRow(id: Long) {
        _rows.value = _rows.value.filterNot { it.id == id }
    }

    /** Clears the in-progress run — after a successful commit, or backing out of the scan flow entirely. */
    fun reset() {
        _rows.value = emptyList()
        _photosScanned.value = 0
        nextId = 0L
    }

    sealed class CommitResult {
        data class Success(val added: Int) : CommitResult()
        data class PartiallyAdded(val added: Int, val skipped: Int) : CommitResult()
    }

    /**
     * Sorts highest delivery number first — the actual drop order, first
     * off the van — then adds each in that order, attaching any notes to
     * its memory entry first so they're there the next time its notes are
     * opened. Clears the run afterwards either way.
     */
    suspend fun commit(): CommitResult {
        val sorted = _rows.value.sortedByDescending { it.number ?: Int.MIN_VALUE }
        var added = 0
        var skipped = 0
        for (row in sorted) {
            if (row.addressText.isBlank()) {
                skipped++
                continue
            }
            val savedId = savedAddressRepository.findOrCreate(row.addressText, "")
            if (row.notes.isNotBlank()) {
                savedAddressRepository.updateNotes(savedId, row.notes, emptyList())
            }
            when (addressRepository.addAddress(row.addressText, "", savedId)) {
                is AddressRepository.AddResult.Success -> added++
                else -> skipped++
            }
        }
        reset()
        return if (skipped == 0) CommitResult.Success(added) else CommitResult.PartiallyAdded(added, skipped)
    }
}
