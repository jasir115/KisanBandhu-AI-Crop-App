package com.example.kisanbandhuai_basedcroprecommendationanddecisionsupportmobileapplication

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

object LocaleHelper {
    
    // Updates both the shared preferences and the AppCompat delegate for modern Android versions
    fun setLocale(context: Context, languageCode: String) {
        // Save preference
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putString("Locale.Helper.Selected.Language", languageCode)
            .apply()
            
        // Apply Locale globally using standard AndroidX API
        val localeList = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    fun getLanguage(context: Context): String {
        return context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getString("Locale.Helper.Selected.Language", "en") ?: "en"
    }

    // Used in BaseActivity to ensure old activities also catch the context change
    fun updateBaseContextLocale(context: Context): Context {
        val language = getLanguage(context)
        val locale = java.util.Locale(language)
        java.util.Locale.setDefault(locale)
        val configuration = android.content.res.Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }
}