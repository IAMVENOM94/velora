package app.velora.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cacheDataStore by preferencesDataStore("response_cache")
@Singleton class ResponseCache @Inject constructor(@ApplicationContext private val context:Context){
    suspend fun put(key:String,json:String){context.cacheDataStore.edit{it[stringPreferencesKey(key)]=json}}
    suspend fun get(key:String)=context.cacheDataStore.data.map{it[stringPreferencesKey(key)]}.first()
    suspend fun clear(){context.cacheDataStore.edit{it.clear()}}
}
