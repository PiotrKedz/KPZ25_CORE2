package com.example.innertemp.util

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable

object ThemeManager {
    private const val PREFS_NAME = "app_settings"
    private const val THEME_KEY = "theme"

    fun getThemePreference(context: Context): String {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPrefs.getString(THEME_KEY, "Light") ?: "Light"
    }

    @Composable
    fun shouldUseDarkTheme(): Boolean {
        val context = androidx.compose.ui.platform.LocalContext.current
        val savedTheme = getThemePreference(context)

        return when (savedTheme) {
            "Dark" -> true
            "Light" -> false
            else -> isSystemInDarkTheme()
        }
    }
}