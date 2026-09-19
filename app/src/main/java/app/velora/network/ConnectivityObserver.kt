package app.velora.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConnectivityObserver @Inject constructor(@ApplicationContext context:Context){
    private val manager=context.getSystemService(ConnectivityManager::class.java)

    val connected:Flow<Boolean> = callbackFlow {
        fun available()=manager.activeNetwork?.let(manager::getNetworkCapabilities)?.let { capabilities ->
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } == true
        val callback=object:ConnectivityManager.NetworkCallback(){
            override fun onAvailable(network:Network){trySend(available())}
            override fun onLost(network:Network){trySend(available())}
            override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities){trySend(available())}
        }
        trySend(available())
        manager.registerDefaultNetworkCallback(callback)
        awaitClose{runCatching{manager.unregisterNetworkCallback(callback)}}
    }.conflate().distinctUntilChanged()
}
