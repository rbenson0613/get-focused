package com.example.get_focused.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

// ✅ Define the single shared DataStore delegate here
val Context.eventsDataStore by preferencesDataStore(name = "events_cache")
