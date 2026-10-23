package com.adamway.app

import android.app.Application
import com.adamway.app.data.AdamWayDatabase
import com.adamway.app.data.AddressRepository
import com.adamway.app.data.SavedAddressRepository
import com.adamway.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A place shared into Adam Way from another app — Google Maps' own Share
 * button, most likely — waiting for the UI to pick it up and offer saving
 * it as a named favorite. Held here rather than in a ViewModel because
 * [MainActivity] receives the Android Intent before any Compose/ViewModel
 * scope exists to hand it to.
 */
data class PendingShare(val addressText: String, val suggestedLabel: String)

class AdamWayApplication : Application() {

    lateinit var addressRepository: AddressRepository
        private set

    lateinit var savedAddressRepository: SavedAddressRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    val pendingShare = MutableStateFlow<PendingShare?>(null)

    override fun onCreate() {
        super.onCreate()
        val db = AdamWayDatabase.get(this)
        savedAddressRepository = SavedAddressRepository(db.savedAddressDao())
        addressRepository = AddressRepository(db.addressDao(), savedAddressRepository)
        settingsRepository = SettingsRepository(this)
    }
}
