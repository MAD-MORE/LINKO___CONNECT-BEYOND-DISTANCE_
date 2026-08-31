package com.linkshare.app.tunnel

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import java.net.DatagramSocket

/** Selects a validated underlying network for LINKO's UDP data plane. */
class LinkoNetworkTransport(context: Context) {
    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun validatedNetwork(): Network? {
        val active = connectivity.activeNetwork ?: return null
        val caps = connectivity.getNetworkCapabilities(active) ?: return null
        return active.takeIf {
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }
    }

    /** Opens a UDP socket bound to the validated network and protected from the VPN. */
    fun openProtectedDatagramSocket(vpnService: VpnService): DatagramSocket {
        val network = validatedNetwork() ?: throw IllegalStateException("linko_no_validated_network")
        val socket = DatagramSocket()
        return try {
            network.bindSocket(socket)
            check(vpnService.protect(socket)) { "linko_failed_to_protect_tunnel_socket" }
            socket
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }

    /** Opens a UDP socket bound to the validated network. */
    fun openDatagramSocket(): DatagramSocket {
        val network = validatedNetwork() ?: throw IllegalStateException("linko_no_validated_network")
        val socket = DatagramSocket()
        return try {
            network.bindSocket(socket)
            socket
        } catch (error: Throwable) {
            socket.close()
            throw error
        }
    }
}
