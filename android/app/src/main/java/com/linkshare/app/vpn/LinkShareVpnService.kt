package com.linkshare.app.vpn

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.linkshare.app.tunnel.ProviderGateway
import com.linkshare.app.tunnel.RelayTunnelClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream

class LinkShareVpnService : VpnService() {
    companion object {
        const val EXTRA_SESSION_ID = "linko.session_id"
        const val EXTRA_PEER_ID = "linko.peer_id"
        const val EXTRA_SESSION_KEY = "linko.session_key"
        const val EXTRA_RELAY_ENDPOINT = "linko.relay_endpoint"
        const val EXTRA_RELAY_TOKEN = "linko.relay_token"
        const val EXTRA_PROVIDER_MODE = "linko.provider_mode"
    }

    private var tunnelInterface: ParcelFileDescriptor? = null
    private var relay: RelayTunnelClient? = null
    private var providerGateway: ProviderGateway? = null
    private var ioJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
        val peerId = intent.getStringExtra(EXTRA_PEER_ID) ?: return START_NOT_STICKY
        val keyText = intent.getStringExtra(EXTRA_SESSION_KEY) ?: return START_NOT_STICKY
        val endpoint = intent.getStringExtra(EXTRA_RELAY_ENDPOINT) ?: return START_NOT_STICKY
        val token = intent.getStringExtra(EXTRA_RELAY_TOKEN) ?: return START_NOT_STICKY
        val providerMode = intent.getBooleanExtra(EXTRA_PROVIDER_MODE, false)
        val key = runCatching { Base64.decode(keyText, Base64.NO_WRAP) }.getOrNull() ?: return START_NOT_STICKY
        if (key.size != 32 || !endpoint.startsWith("wss://") || token.isBlank()) return START_NOT_STICKY

        tunnelInterface?.close(); tunnelInterface = null
        relay?.close(); relay = null
        providerGateway?.close(); providerGateway = null

        if (providerMode) {
            providerGateway = ProviderGateway { packet -> relay?.sendPacket(packet) == true }
            relay = RelayTunnelClient(endpoint, token, sessionId, peerId, key) { packet ->
                providerGateway?.forwardIpv4(packet)
            }
            ioJob?.cancel()
            ioJob = CoroutineScope(Dispatchers.IO).launch {
                if (relay?.connect() != true) stopSelf()
            }
            return START_NOT_STICKY
        }

        relay = RelayTunnelClient(endpoint, token, sessionId, peerId, key) { packet ->
            runCatching {
                FileOutputStream(tunnelInterface?.fileDescriptor ?: return@runCatching).use { output ->
                    output.write(packet)
                    output.flush()
                }
            }
        }
        tunnelInterface = Builder()
            .setSession("LINKO tunnel")
            .addAddress("10.48.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .establish()

        val pfd = tunnelInterface ?: return START_NOT_STICKY
        ioJob?.cancel()
        ioJob = CoroutineScope(Dispatchers.IO).launch {
            val connected = relay?.connect() == true
            if (!connected) {
                stopSelf()
                return@launch
            }
            FileInputStream(pfd.fileDescriptor).use { input ->
                val packet = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(packet)
                    if (count <= 0) break
                    if (relay?.sendPacket(packet.copyOf(count)) != true) break
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        ioJob?.cancel(); ioJob = null
        providerGateway?.close(); providerGateway = null
        relay?.close(); relay = null
        tunnelInterface?.close(); tunnelInterface = null
        super.onDestroy()
    }
}
