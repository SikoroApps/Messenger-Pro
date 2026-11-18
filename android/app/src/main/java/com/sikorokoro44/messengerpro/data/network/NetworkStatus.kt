package com.sikorokoro44.messengerpro.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** No sign-in request is attempted while the device reports no usable transport. */
fun interface NetworkStatus {
    fun isOnline(): Boolean
}

class AndroidNetworkStatus(context: Context) : NetworkStatus {
    private val manager = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    override fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
