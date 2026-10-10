package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.data.api.GeminiModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_settings_prefs", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(getSavedApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _defaultModel = MutableStateFlow(getSavedModel())
    val defaultModel: StateFlow<String> = _defaultModel.asStateFlow()

    private val _customInstructions = MutableStateFlow(getSavedCustomInstructions())
    val customInstructions: StateFlow<String> = _customInstructions.asStateFlow()

    private val _temperature = MutableStateFlow(getSavedTemperature())
    val temperature: StateFlow<Float> = _temperature.asStateFlow()

    fun getSavedApiKey(): String {
        return prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
    }

    fun setApiKey(key: String) {
        val trimmed = key.trim()
        prefs.edit().putString(KEY_CUSTOM_API_KEY, trimmed).apply()
        _apiKey.value = trimmed
    }

    fun getSavedModel(): String {
        return prefs.getString(KEY_DEFAULT_MODEL, GeminiModels.FLASH) ?: GeminiModels.FLASH
    }

    fun setDefaultModel(model: String) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, model).apply()
        _defaultModel.value = model
    }

    fun getSavedCustomInstructions(): String {
        return prefs.getString(KEY_CUSTOM_INSTRUCTIONS, "") ?: ""
    }

    fun setCustomInstructions(instructions: String) {
        prefs.edit().putString(KEY_CUSTOM_INSTRUCTIONS, instructions).apply()
        _customInstructions.value = instructions
    }

    fun getSavedTemperature(): Float {
        return prefs.getFloat(KEY_TEMPERATURE, 0.7f)
    }

    fun setTemperature(temp: Float) {
        prefs.edit().putFloat(KEY_TEMPERATURE, temp).apply()
        _temperature.value = temp
    }

    companion object {
        private const val KEY_CUSTOM_API_KEY = "key_custom_api_key"
        private const val KEY_DEFAULT_MODEL = "key_default_model"
        private const val KEY_CUSTOM_INSTRUCTIONS = "key_custom_instructions"
        private const val KEY_TEMPERATURE = "key_temperature"
    }
}
