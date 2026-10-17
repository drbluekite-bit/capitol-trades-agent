package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamway.app.data.Address
import com.adamway.app.data.AddressRepository
import com.adamway.app.data.SavedAddress
import com.adamway.app.data.SavedAddressRepository
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

    fun addAddress(addressText: String, what3words: String, savedAddressId: Long? = null) {
        viewModelScope.launch {
            when (repository.addAddress(addressText, what3words, savedAddressId)) {
                is AddressRepository.AddResult.Success -> _events.send(AddressEvent.Added)
                is AddressRepository.AddResult.QueueFull -> _events.send(AddressEvent.QueueFull)
                is AddressRepository.AddResult.BlankAddress -> _events.send(AddressEvent.BlankAddress)
            }
        }
    }

    /**
     * One-tap add for a favorite/quick-add location. Deliberately doesn't
     * go through [events] — that channel is only meant for the Add/Edit
     * screen to react to (e.g. close itself on success), and a stale
     * unread event sent while that screen isn't even open would otherwise
     * sit buffered and fire against it later, the next time it opens.
     */
    fun quickAdd(addressText: String) {
        viewModelScope.launch { repository.addAddress(addressText, what3words = "") }
    }

    fun updateAddress(address: Address) {
        viewModelScope.launch { repository.updateAddress(address) }
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
     * Live candidate address lines matching [query] — like typing into
     * Google Maps' own search bar. Text only, never a coordinate: see
     * [NominatimClient]'s class doc for why.
     */
    suspend fun searchAddressSuggestions(query: String): List<String> = nominatimClient.suggest(query)
}
