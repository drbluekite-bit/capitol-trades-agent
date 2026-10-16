package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamway.app.data.Address
import com.adamway.app.data.AddressRepository
import com.adamway.app.data.SavedAddress
import com.adamway.app.data.SavedAddressRepository
import com.adamway.app.location.AddressSuggestion
import com.adamway.app.location.NominatimClient
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class AddressEvent {
    object Added : AddressEvent()
    object QueueFull : AddressEvent()
    object BlankAddress : AddressEvent()
}

class AddressViewModel(
    private val repository: AddressRepository,
    private val savedAddressRepository: SavedAddressRepository,
    private val nominatimClient: NominatimClient = NominatimClient(),
) : ViewModel() {

    val queue: StateFlow<List<Address>> = repository.observeQueue()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = Channel<AddressEvent>(Channel.BUFFERED)
    val events: Flow<AddressEvent> = _events.receiveAsFlow()

    var editingAddress = MutableStateFlow<Address?>(null)
        private set

    val maxQueueSize = AddressRepository.MAX_QUEUE_SIZE

    fun startEditing(address: Address?) {
        editingAddress.value = address
    }

    /**
     * [resolvedLat]/[resolvedLng] is a location the user picked themselves
     * from the address search results — pass it when available so the
     * stop never needs geocoding again at journey time.
     */
    fun addAddress(
        houseNumber: String,
        houseName: String,
        postcode: String,
        what3words: String,
        savedAddressId: Long? = null,
        resolvedLat: Double? = null,
        resolvedLng: Double? = null,
    ) {
        viewModelScope.launch {
            when (repository.addAddress(houseNumber, houseName, postcode, what3words, savedAddressId, resolvedLat, resolvedLng)) {
                is AddressRepository.AddResult.Success -> _events.send(AddressEvent.Added)
                is AddressRepository.AddResult.QueueFull -> _events.send(AddressEvent.QueueFull)
                is AddressRepository.AddResult.BlankAddress -> _events.send(AddressEvent.BlankAddress)
            }
        }
    }

    fun updateAddress(address: Address, resolvedLat: Double? = null, resolvedLng: Double? = null) {
        viewModelScope.launch { repository.updateAddress(address, resolvedLat, resolvedLng) }
    }

    fun deleteAddress(address: Address) {
        viewModelScope.launch { repository.deleteAddress(address) }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { repository.reorder(fromIndex, toIndex) }
    }

    fun clearAll() {
        viewModelScope.launch { repository.deleteAll() }
    }

    /** Previously-used addresses from memory that loosely match [query], for autocomplete. */
    suspend fun suggestAddresses(query: String): List<SavedAddress> = savedAddressRepository.search(query)

    /**
     * Live candidate addresses matching [query] from the map data itself —
     * like typing into Google Maps' own search bar — for the user to
     * confirm the exact one rather than Adam Way guessing.
     */
    suspend fun searchMapAddresses(query: String): List<AddressSuggestion> = nominatimClient.suggest(query)
}
