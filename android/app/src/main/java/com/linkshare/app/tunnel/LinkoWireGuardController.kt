package com.linkshare.app.tunnel

import android.content.Context
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/**
 * Small LINKO-owned adapter around the official WireGuard Android tunnel library.
 * It deliberately contains no relay logic and never persists the private key in a config file.
 */
class LinkoWireGuardController(context: Context) : AutoCloseable {
    private val backend = GoBackend(context.applicationContext)
    private var activeTunnel: Tunnel? = null

    @Synchronized
    fun start(
        name: String,
        privateKeyBase64: String,
        localAddress: String,
        peerPublicKeyBase64: String,
        peerAddress: String,
        endpointHost: String,
        endpointPort: Int,
        allowedIps: String = "0.0.0.0/0",
        dns: String = "1.1.1.1",
        mtu: Int = 1280,
    ) {
        require(name.isNotBlank()) { "wireguard_tunnel_name_required" }
        require(endpointHost.isNotBlank()) { "wireguard_endpoint_host_required" }
        require(endpointPort in 1..65535) { "wireguard_endpoint_port_invalid" }
        require(localAddress.contains('/')) { "wireguard_local_address_invalid" }
        require(peerAddress.contains('/')) { "wireguard_peer_address_invalid" }

        stop()
        val tunnel = NamedTunnel(name)
        val configText = buildConfig(
            privateKeyBase64 = privateKeyBase64,
            localAddress = localAddress,
            peerPublicKeyBase64 = peerPublicKeyBase64,
            peerAddress = peerAddress,
            endpointHost = endpointHost,
            endpointPort = endpointPort,
            allowedIps = allowedIps,
            dns = dns,
            mtu = mtu,
        )
        val config = Config.parse(ByteArrayInputStream(configText.toByteArray(StandardCharsets.UTF_8)))
        try {
            backend.setState(tunnel, Tunnel.State.UP, config)
            activeTunnel = tunnel
        } catch (error: BackendException) {
            throw IllegalStateException("wireguard_backend_${error.reason}", error)
        } catch (error: Exception) {
            throw IllegalStateException("wireguard_start_failed", error)
        }
    }

    @Synchronized
    fun stop() {
        val tunnel = activeTunnel ?: return
        runCatching { backend.setState(tunnel, Tunnel.State.DOWN, null) }
        activeTunnel = null
    }

    fun isRunning(): Boolean = synchronized(this) { activeTunnel?.let { backend.getState(it) == Tunnel.State.UP } ?: false }

    override fun close() = stop()

    private fun buildConfig(
        privateKeyBase64: String,
        localAddress: String,
        peerPublicKeyBase64: String,
        peerAddress: String,
        endpointHost: String,
        endpointPort: Int,
        allowedIps: String,
        dns: String,
        mtu: Int,
    ): String = buildString {
        appendLine("[Interface]")
        appendLine("PrivateKey = ${privateKeyBase64.trim()}")
        appendLine("Address = ${localAddress.trim()}")
        appendLine("DNS = ${dns.trim()}")
        appendLine("MTU = ${mtu.coerceIn(1280, 1420)}")
        appendLine()
        appendLine("[Peer]")
        appendLine("PublicKey = ${peerPublicKeyBase64.trim()}")
        appendLine("AllowedIPs = ${allowedIps.trim()}")
        appendLine("Endpoint = ${formatEndpoint(endpointHost.trim(), endpointPort)}")
        appendLine("PersistentKeepalive = 25")
        appendLine("# LINKO peer address: ${peerAddress.trim()}")
    }

    private fun formatEndpoint(host: String, port: Int): String =
        if (host.contains(':') && !host.startsWith('[')) "[$host]:$port" else "$host:$port"

    private class NamedTunnel(private val name: String) : Tunnel {
        override fun getName(): String = name
        override fun onStateChange(newState: Tunnel.State) = Unit
    }
}
