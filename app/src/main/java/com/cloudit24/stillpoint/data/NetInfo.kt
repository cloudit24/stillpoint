package com.cloudit24.stillpoint.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address

/** The phone's address on the current network, e.g. ("Wi-Fi", "192.168.1.23"). */
data class LocalIp(val kind: String, val address: String)

object NetInfo {
    /** Address of the active network, IPv4 preferred. Null when offline. Local only: nothing goes online. */
    fun localIp(context: Context): LocalIp? = runCatching {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(network)
        val addrs = cm.getLinkProperties(network)?.linkAddresses?.map { it.address }
            ?.filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            .orEmpty()
        val addr = addrs.firstOrNull { it is Inet4Address } ?: addrs.firstOrNull() ?: return null
        val kind = when {
            caps == null -> "Local"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Local"
        }
        LocalIp(kind, addr.hostAddress?.substringBefore('%') ?: return null)
    }.getOrNull()
}
