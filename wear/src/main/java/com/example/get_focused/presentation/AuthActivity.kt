package com.example.get_focused.presentation

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.calendar.CalendarScopes

class AuthActivity : ComponentActivity() {

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                task.getResult(ApiException::class.java)
                setResult(Activity.RESULT_OK)
                finish()
            } catch (e: ApiException) {
                Log.e(TAG, "Sign-in failed after result OK", e)
                setResult(Activity.RESULT_CANCELED)
                finish()
            }
        } else {
            Log.w(TAG, "Sign-in flow was cancelled or failed. Result code: ${result.resultCode}")
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gso: GoogleSignInOptions by lazy {
            // This is your Web Client ID from Google Cloud Console, NOT your Android Client ID.
            // It's best practice to store this in your strings.xml file.
            val serverClientId = ""
                //getString(R.string.server_client_id)

            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                // This line asks for permission to read calendar events
                .requestScopes(Scope("https://www.googleapis.com/auth/calendar.readonly"))
                // --- ADD THIS LINE ---
                // This line requests a one-time code that your backend server can exchange
                // for an access and refresh token.
                .requestServerSideAccess(serverClientId)
                .build()
        }

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    companion object {
        private const val TAG = "AuthActivity"
    }
}