package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.adamway.app.AdamWayApplication
import com.adamway.app.location.AddressLocationResolver
import com.adamway.app.location.What3WordsClient

class AdamWayViewModelFactory(private val app: AdamWayApplication) : ViewModelProvider.Factory {

    private val locationResolver by lazy {
        AddressLocationResolver(What3WordsClient { app.settingsRepository.what3wordsApiKey.value })
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            AddressViewModel::class.java -> AddressViewModel(app.addressRepository) as T
            JourneyViewModel::class.java -> JourneyViewModel(app.addressRepository, locationResolver) as T
            SettingsViewModel::class.java -> SettingsViewModel(app.settingsRepository) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: $modelClass")
        }
    }
}
