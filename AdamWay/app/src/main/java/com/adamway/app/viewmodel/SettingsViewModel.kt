package com.adamway.app.viewmodel

import androidx.lifecycle.ViewModel
import com.adamway.app.data.PreferredNavApp
import com.adamway.app.data.SettingsRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val what3wordsApiKey: StateFlow<String> = repository.what3wordsApiKey
    val preferredNavApp: StateFlow<PreferredNavApp> = repository.preferredNavApp

    fun setWhat3wordsApiKey(key: String) = repository.setWhat3wordsApiKey(key)

    fun setPreferredNavApp(app: PreferredNavApp) = repository.setPreferredNavApp(app)
}
