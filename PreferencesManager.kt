package com.robot.ai.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "robot_settings")

object PreferencesManager {
    private val BACKEND_URL_KEY = stringPreferencesKey("backend_url")
    private val TASK_ID_KEY = stringPreferencesKey("current_task_id")
    
    fun getBackendUrl(context: Context): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[BACKEND_URL_KEY] ?: "ws://10.0.2.2:8000/ws/agent"
            }
    }
    
    suspend fun setBackendUrl(context: Context, url: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKEND_URL_KEY] = url
        }
    }
    
    fun getCurrentTaskId(context: Context): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[TASK_ID_KEY] ?: "default_task"
            }
    }
    
    suspend fun setCurrentTaskId(context: Context, taskId: String) {
        context.dataStore.edit { preferences ->
            preferences[TASK_ID_KEY] = taskId
        }
    }
}
