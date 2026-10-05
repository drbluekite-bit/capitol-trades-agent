package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adamway.app.data.SavedAddress
import com.adamway.app.data.SavedAddressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SavedAddressViewModel(private val repository: SavedAddressRepository) : ViewModel() {

    private val _address = MutableStateFlow<SavedAddress?>(null)
    val address: StateFlow<SavedAddress?> = _address.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch { _address.value = repository.getById(id) }
    }

    fun saveNotes(notes: String) {
        val current = _address.value ?: return
        viewModelScope.launch {
            repository.updateNotes(current.id, notes, current.photoPathList)
            _address.value = current.copy(notes = notes)
        }
    }

    fun addPhoto(filePath: String) {
        val current = _address.value ?: return
        val updatedPaths = current.photoPathList + filePath
        viewModelScope.launch {
            repository.updateNotes(current.id, current.notes, updatedPaths)
            _address.value = repository.getById(current.id)
        }
    }

    fun removePhoto(filePath: String) {
        val current = _address.value ?: return
        val updatedPaths = current.photoPathList - filePath
        viewModelScope.launch {
            repository.updateNotes(current.id, current.notes, updatedPaths)
            repository.deletePhotoFile(filePath)
            _address.value = repository.getById(current.id)
        }
    }
}
