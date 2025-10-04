package com.example.get_focused.presentation

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.api.services.calendar.CalendarScopes
import com.google.android.gms.common.api.Scope

class AuthActivity : ComponentActivity() {

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                // The task is successful, which means the user has signed in.
                // We don't need the account object here, just the success signal.
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

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(CalendarScopes.CALENDAR_READONLY))
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    companion object {
        private const val TAG = "AuthActivity"
    }
}