package com.simohypers.limusic.common

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

/** Feeds [Connectivity] from Android's default network for the life of the process. Call once, at launch. */
fun watchConnectivity(context: Context) {
    // activeNetwork / networkHandle (API 23) and registerDefaultNetworkCallback (API 24) do not exist
    // on Android 5 and 6. There Connectivity keeps its optimistic default and is never fed.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
    fun validated(capabilities: NetworkCapabilities?) =
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    // Seeded now, so nothing reads the optimistic default once the app is running.
    val active = manager.activeNetwork
    Connectivity.update(active?.networkHandle, validated(manager.getNetworkCapabilities(active)))
    manager.registerDefaultNetworkCallback(
        object : ConnectivityManager.NetworkCallback() {
            private var blocked = false

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                if (!blocked) Connectivity.update(network.networkHandle, validated(networkCapabilities))
            }

            // Android cuts a background app off the network on some phones (battery saver, data saver,
            // Samsung's sleeping apps): the network is still there, but every lookup fails with
            // EAI_NODATA. For the app that is offline, so retry loops wait instead of spinning (issue #7).
            override fun onBlockedStatusChanged(
                network: Network,
                blocked: Boolean,
            ) {
                this.blocked = blocked
                if (blocked) {
                    Connectivity.update(null, false)
                } else {
                    Connectivity.update(network.networkHandle, validated(manager.getNetworkCapabilities(network)))
                }
            }

            override fun onLost(network: Network) = Connectivity.lost(network.networkHandle)
        },
    )
}
