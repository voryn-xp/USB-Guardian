package com.vorynxp.usbguardian.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    LIGHT, DARK, SYSTEM
}

enum class DeviceRule {
    ALLOWED, BLOCKED, UNKNOWN
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "usb_guardian_settings")

@Singleton
class UserPreferences @Inject constructor(private val context: Context) {

    companion object {
        private val MASTER_TOGGLE_KEY = booleanPreferencesKey("master_usb_blocking_enabled")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private const val RULE_PREFIX = "rule_"
    }

    val masterToggleFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[MASTER_TOGGLE_KEY] ?: true // Default to active protection
    }

    suspend fun setMasterToggle(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MASTER_TOGGLE_KEY] = enabled
        }
    }

    val themeModeFlow: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val themeStr = preferences[THEME_MODE_KEY] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(themeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    /**
     * Gets the authorization rule for a device.
     */
    fun getDeviceRuleFlow(vendorId: Int, productId: Int): Flow<DeviceRule> {
        val key = stringPreferencesKey("$RULE_PREFIX${vendorId}_$productId")
        return context.dataStore.data.map { preferences ->
            val ruleStr = preferences[key] ?: DeviceRule.UNKNOWN.name
            try {
                DeviceRule.valueOf(ruleStr)
            } catch (e: Exception) {
                DeviceRule.UNKNOWN
            }
        }
    }

    /**
     * Gets all rules stored in DataStore as a map of "vendorId:productId" -> DeviceRule
     */
    val allRulesFlow: Flow<Map<String, DeviceRule>> = context.dataStore.data.map { preferences ->
        preferences.asMap()
            .filterKeys { it.name.startsWith(RULE_PREFIX) }
            .map { (key, value) ->
                val vidPid = key.name.removePrefix(RULE_PREFIX).replace("_", ":")
                val rule = try {
                    DeviceRule.valueOf(value as String)
                } catch (e: Exception) {
                    DeviceRule.UNKNOWN
                }
                vidPid to rule
            }.toMap()
    }

    suspend fun setDeviceRule(vendorId: Int, productId: Int, rule: DeviceRule) {
        val key = stringPreferencesKey("$RULE_PREFIX${vendorId}_$productId")
        context.dataStore.edit { preferences ->
            preferences[key] = rule.name
        }
    }
}
