package com.adamway.app

import android.app.Application
import com.adamway.app.data.AdamWayDatabase
import com.adamway.app.data.AddressRepository
import com.adamway.app.data.SettingsRepository

class AdamWayApplication : Application() {

    lateinit var addressRepository: AddressRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        val db = AdamWayDatabase.get(this)
        addressRepository = AddressRepository(db.addressDao())
        settingsRepository = SettingsRepository(this)
    }
}
