package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamway.app.data.Address
import com.adamway.app.data.AddressRepository
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

class AddressViewModel(private val repository: AddressRepository) : ViewModel() {

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

    fun addAddress(houseNumber: String, houseName: String, postcode: String, what3words: String) {
        viewModelScope.launch {
            when (repository.addAddress(houseNumber, houseName, postcode, what3words)) {
                is AddressRepository.AddResult.Success -> _events.send(AddressEvent.Added)
                is AddressRepository.AddResult.QueueFull -> _events.send(AddressEvent.QueueFull)
                is AddressRepository.AddResult.BlankAddress -> _events.send(AddressEvent.BlankAddress)
            }
        }
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
}
