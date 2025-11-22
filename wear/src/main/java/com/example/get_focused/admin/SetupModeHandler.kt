package com.example.get_focused.admin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.example.get_focused.presentation.theme.Get_FocusedTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.setupModeDataStore by preferencesDataStore(name = "setup_mode")

/**
 * Manages setup mode for the watch
 * During setup mode, restrictions are relaxed to allow account configuration
 */
class SetupModeHandler(private val context: Context) {

    private val deviceOwnerManager = DeviceOwnerManager(context)
    private val SETUP_COMPLETE_KEY = booleanPreferencesKey("setup_complete")

    companion object {
        private const val TAG = "SetupModeHandler"
    }

    /**
     * Check if initial setup has been completed
     */
    suspend fun isSetupComplete(): Boolean {
        return context.setupModeDataStore.data
            .map { prefs -> prefs[SETUP_COMPLETE_KEY] ?: false }
            .first()
    }

    /**
     * Mark setup as complete and enable full restrictions
     */
    suspend fun completeSetup() {
        Log.d(TAG, "Completing setup - enabling full restrictions")

        // Save setup complete flag
        context.setupModeDataStore.edit { prefs ->
            prefs[SETUP_COMPLETE_KEY] = true
        }

        // Enable full parental controls (with Google account allowed)
        deviceOwnerManager.setupParentalControls(allowGoogleAccount = true)

        Log.d(TAG, "Setup complete - device locked down")
    }

    /**
     * Enter setup mode (called during initial device owner provisioning)
     */
    suspend fun enterSetupMode() {
        Log.d(TAG, "Entering setup mode - relaxing restrictions")

        // Mark setup as incomplete
        context.setupModeDataStore.edit { prefs ->
            prefs[SETUP_COMPLETE_KEY] = false
        }

        // Allow account modifications during setup
        if (deviceOwnerManager.isDeviceOwner()) {
            deviceOwnerManager.setUserRestrictions(mapOf(
                android.os.UserManager.DISALLOW_MODIFY_ACCOUNTS to false,
                android.os.UserManager.DISALLOW_FACTORY_RESET to true  // Still prevent factory reset
            ))
        }

        Log.d(TAG, "Setup mode active - user can add Google account")
    }

    /**
     * Temporarily enter setup mode for maintenance (e.g., parent needs to change account)
     * This should be triggered from the parent's mobile app with authentication
     */
    suspend fun temporarySetupMode(durationMinutes: Int = 5) {
        Log.d(TAG, "Entering temporary setup mode for $durationMinutes minutes")

        deviceOwnerManager.temporarilyAllowAccountModification(
            durationMillis = durationMinutes * 60 * 1000L
        )
    }
}

/**
 * Activity shown on first launch to guide through setup
 */
class InitialSetupActivity : ComponentActivity() {

    private lateinit var setupModeHandler: SetupModeHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupModeHandler = SetupModeHandler(this)

        // Check if this is first launch
        lifecycleScope.launch {
            val isSetupComplete = setupModeHandler.isSetupComplete()

            if (!isSetupComplete) {
                showSetupScreen()
            } else {
                // Setup already done, go to main activity
                startMainActivity()
            }
        }
    }

    private suspend fun showSetupScreen() {
        // Enter setup mode (allow account addition)
        setupModeHandler.enterSetupMode()

        // Show setup UI
        setContent {
            SetupScreen(
                onSetupComplete = {
                    lifecycleScope.launch {
                        setupModeHandler.completeSetup()
                        startMainActivity()
                    }
                }
            )
        }
    }

    private fun startMainActivity() {
        val intent = Intent(this,
            Class.forName("com.example.get_focused.presentation.MainActivity"))
        startActivity(intent)
        finish()
    }
}

/**
 * Composable setup screen
 */
@Composable
fun SetupScreen(onSetupComplete: () -> Unit) {
    Get_FocusedTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Setup Your Watch",
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "1. Add your Google account\n2. Connect to WiFi\n3. Tap 'Complete Setup' when done",
                style = MaterialTheme.typography.body2,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onSetupComplete
            ) {
                Text("Complete Setup")
            }
        }
    }
}