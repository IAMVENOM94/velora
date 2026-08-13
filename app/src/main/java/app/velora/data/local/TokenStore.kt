package app.velora.data.local

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore("secure_session")
data class StoredTokens(val access:String,val refresh:String)

@Singleton
class TokenStore @Inject constructor(@ApplicationContext private val context:Context,private val cipher:TokenCipher){
    private val accessKey=stringPreferencesKey("access_token")
    private val refreshKey=stringPreferencesKey("refresh_token")
    val tokens:Flow<StoredTokens?> = context.sessionDataStore.data.map { p -> runCatching{p[accessKey]?.let { access -> p[refreshKey]?.let { StoredTokens(cipher.decrypt(access),cipher.decrypt(it)) } }}.getOrNull() }
    val hasSession:Flow<Boolean> = tokens.map { it != null }
    suspend fun current():StoredTokens?{
        val raw=context.sessionDataStore.data.first()
        val access=raw[accessKey]?:return null
        val refresh=raw[refreshKey]?:return null
        val value=runCatching{StoredTokens(cipher.decrypt(access),cipher.decrypt(refresh))}.getOrNull()?:return null
        if(!cipher.isEncrypted(access)||!cipher.isEncrypted(refresh))save(value.access,value.refresh)
        return value
    }
    suspend fun save(access:String,refresh:String){context.sessionDataStore.edit{it[accessKey]=cipher.encrypt(access);it[refreshKey]=cipher.encrypt(refresh)}}
    suspend fun clear(){context.sessionDataStore.edit{it.remove(accessKey);it.remove(refreshKey)}}
}
