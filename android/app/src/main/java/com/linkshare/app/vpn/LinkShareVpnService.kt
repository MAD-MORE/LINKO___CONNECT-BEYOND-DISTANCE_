package com.linkshare.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import com.linkshare.app.MainActivity
import com.linkshare.app.R
import com.linkshare.app.auth.LinkoAuth
import com.linkshare.app.network.ConnectionSeverity
import com.linkshare.app.network.ConnectionStage
import com.linkshare.app.network.DirectP2pNegotiator
import com.linkshare.app.network.LinkoDeviceControlApi
import com.linkshare.app.network.LinkoEngineBridge
import com.linkshare.app.network.LinkoNetworkException
import com.linkshare.app.network.LinkoSignalingClient
import com.linkshare.app.tunnel.EncryptedDatagramTunnel
import com.linkshare.app.tunnel.IpPacketRouter
import kotlinx.coroutines.runBlocking
import java.net.DatagramSocket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Receiver VPN. Supabase is signaling only; the data path is direct authenticated UDP. */
class LinkShareVpnService : VpnService() {
    @Volatile private var tunnelInterface: ParcelFileDescriptor? = null
    @Volatile private var transport: EncryptedDatagramTunnel? = null
    private val running = AtomicBoolean(false)
    private val terminalFailure = AtomicBoolean(false)
    private val recoveryInProgress = AtomicBoolean(false)
    private val sessionGeneration = AtomicLong(0L)
    private val executor = Executors.newFixedThreadPool(3)
    private var scheduler: ScheduledExecutorService? = null
    private val router = IpPacketRouter()
    private val bytesUp = AtomicLong(0)
    private val bytesDown = AtomicLong(0)
    private val lastPongReceivedAt = AtomicLong(0)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null
    @Volatile private var activeSessionId: String? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            val notification = serviceNotification("LINKO", "Preparing direct encrypted tunnel")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (error: Throwable) {
            Log.e(TAG, "LINKO_VPN_FOREGROUND_START_FAILED", error)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID)?.trim()
        val role = intent?.getStringExtra(EXTRA_ROLE)
        val sessionKey = intent?.getByteArrayExtra(EXTRA_SESSION_KEY)
        if (sessionId.isNullOrBlank() || role != ROLE_RECEIVER || sessionKey?.size != 32) {
            Log.e(TAG, "Invalid direct VPN startup arguments")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (VpnService.prepare(this) != null) {
            Log.e(TAG, "VPN permission not granted by user")
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val generation = sessionGeneration.incrementAndGet()
        stopTunnel(reportStoppedState = false)
        activeSessionId = sessionId
        terminalFailure.set(false)
        recoveryInProgress.set(false)
        acquireLocks()

        running.set(false)
        LinkoEngineBridge.reportTunnelState("direct_connecting", "Finding a direct peer path")
        executor.execute {
            establishDirectTransportWithRecovery(sessionId, sessionKey.copyOf(), intent, generation)
        }
        return START_STICKY
    }

    private fun establishDirectTransportWithRecovery(
        sessionId: String,
        sessionKey: ByteArray,
        startupIntent: Intent?,
        generation: Long,
    ) {
        if (!recoveryInProgress.compareAndSet(false, true)) return
        var lastReason = "direct_connection_failed"
        try {
            for (attempt in 1..AUTO_RECOVERY_MAX_ATTEMPTS) {
                if (!isCurrent(sessionId, generation)) return

                if (attempt > 1) {
                    val delayMs = (AUTO_RECOVERY_INITIAL_DELAY_MS * (1L shl (attempt - 2))).coerceAtMost(AUTO_RECOVERY_MAX_DELAY_MS)
                    LinkoEngineBridge.reportTunnelState("reconnecting", "Trying the connection again")
                    LinkoEngineBridge.reportConnectionDiagnostic(
                        ConnectionStage.ICE_CHECKING,
                        "AUTO_RECOVERY_ATTEMPT",
                        "Retrying direct connection attempt $attempt/$AUTO_RECOVERY_MAX_ATTEMPTS",
                        ConnectionSeverity.INFO,
                        mapOf("attempt" to attempt.toString()),
                    )
                    try { Thread.sleep(delayMs) } catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); return }
                    if (!isCurrent(sessionId, generation)) return
                }

                val socket = try {
                    DatagramSocket(0).apply { reuseAddress = true }
                } catch (error: Exception) {
                    lastReason = error.message?.takeIf { it.isNotBlank() } ?: "udp_socket_creation_failed"
                    Log.w(TAG, "AUTO_RECOVERY_SOCKET_CREATE_FAILED session=$sessionId attempt=$attempt/$AUTO_RECOVERY_MAX_ATTEMPTS reason=$lastReason")
                    continue
                }

                try {
                    if (!protect(socket)) throw LinkoNetworkException("udp_socket_protection_failed")
                    Log.i(TAG, "AUTO_RECOVERY_SOCKET_READY session=$sessionId attempt=$attempt/$AUTO_RECOVERY_MAX_ATTEMPTS port=${socket.localPort}")
                    establishDirectTransport(sessionId, sessionKey.copyOf(), socket, startupIntent, generation)
                    return
                } catch (stale: CancellationExceptionLike) {
                    runCatching { socket.close() }
                    return
                } catch (error: Exception) {
                    lastReason = error.message?.takeIf { it.isNotBlank() } ?: "direct_connection_failed"
                    Log.w(TAG, "AUTO_RECOVERY_ATTEMPT_FAILED session=$sessionId attempt=$attempt/$AUTO_RECOVERY_MAX_ATTEMPTS reason=$lastReason")
                    LinkoEngineBridge.reportConnectionDiagnostic(ConnectionStage.ICE_CHECKING, "AUTO_RECOVERY_ATTEMPT_FAILED", "Direct connection attempt $attempt failed; retrying safely", ConnectionSeverity.WARNING, mapOf("attempt" to attempt.toString(), "reason" to lastReason.take(96)))
                    runCatching { socket.close() }
                    if (!isCurrent(sessionId, generation)) return
                }
            }

            if (isCurrent(sessionId, generation)) {
                LinkoEngineBridge.reportConnectionDiagnostic(ConnectionStage.ICE_CHECKING, "AUTO_RECOVERY_EXHAUSTED", "Automatic connection recovery exhausted", ConnectionSeverity.ERROR, mapOf("attempts" to AUTO_RECOVERY_MAX_ATTEMPTS.toString()))
                failSession(sessionId, generation, lastReason)
            }
        } finally { recoveryInProgress.set(false) }
    }

    private fun establishDirectTransport(sessionId: String, sessionKey: ByteArray, socket: DatagramSocket, startupIntent: Intent?, generation: Long) {
        try {
            assertCurrent(sessionId, generation)
            val auth = LinkoAuth(applicationContext)
            val token = auth.currentAccessToken()?.takeIf { it.isNotBlank() } ?: throw LinkoNetworkException("device_auth_required")
            val result = runBlocking {
                DirectP2pNegotiator.establish(sessionId, sessionKey, EncryptedDatagramTunnel.Role.RECEIVER, LinkoSignalingClient(accessToken = token), socket)
            }
            assertCurrent(sessionId, generation)

            LinkoEngineBridge.reportTunnelState("direct_verified", "Authenticated direct path verified")
            val allowedPackages = startupIntent?.getStringArrayListExtra(EXTRA_ALLOWED_PACKAGES) ?: emptyList<String>()
            val builder = Builder().setSession("LINKO Direct Tunnel").setMtu(TUN_MTU).addAddress("10.48.0.2", 32).addRoute("0.0.0.0", 0).addRoute("::", 0).addDnsServer("1.1.1.1").addDnsServer("8.8.8.8").setBlocking(true)
            for (pkg in allowedPackages) runCatching { builder.addAllowedApplication(pkg) }
            val newInterface = builder.establish() ?: throw LinkoNetworkException("vpn_interface_establish_failed")
            assertCurrent(sessionId, generation)
            tunnelInterface = newInterface
            transport = EncryptedDatagramTunnel(result.socket, result.peer, sessionId, EncryptedDatagramTunnel.Role.RECEIVER, sessionKey)
            assertCurrent(sessionId, generation)
            running.set(true)
            lastPongReceivedAt.set(0L)
            executor.execute { outboundLoop() }
            executor.execute { inboundLoop() }
            if (!verifyBidirectionalTransport(sessionId, generation)) throw LinkoNetworkException("DIRECT_DATA_PLANE_PROBE_FAILED")
            runCatching { runBlocking { LinkoDeviceControlApi(applicationContext).transition(sessionId, "connected") } }
                .onFailure { Log.w(TAG, "Connected tunnel established but session-state update failed: ${it.message}") }
            updateForegroundNotification("Connected", "Direct encrypted LINKO tunnel is active")
            LinkoEngineBridge.reportTunnelState("direct_established", "Secure direct connection established")
            registerNetworkHandoverCallback(result.socket, sessionId, generation)
            scheduler = Executors.newSingleThreadScheduledExecutor()
            scheduler?.scheduleWithFixedDelay({
                if (running.get() && isCurrent(sessionId, generation)) {
                    val sentAt = System.currentTimeMillis()
                    runCatching { transport?.sendPing(sentAt) }
                    val silentMs = if (lastPongReceivedAt.get() == 0L) Long.MAX_VALUE else System.currentTimeMillis() - lastPongReceivedAt.get()
                    if (silentMs > HEARTBEAT_TIMEOUT_MS) {
                        LinkoEngineBridge.reportConnectionDiagnostic(ConnectionStage.CONNECTED, "NETWORK_LOST", "Direct peer heartbeat is not responding", ConnectionSeverity.WARNING)
                        if (silentMs > HEARTBEAT_DEAD_MS) failSession(sessionId, generation, "peer_heartbeat_timeout")
                    }
                }
            }, 3, 15, TimeUnit.SECONDS)
            LinkoEngineBridge.reportTunnelState("connected", "Direct tunnel established; packet flow verified")
            Log.i(TAG, "LINKO direct P2P tunnel established and verified for session=$sessionId to ${result.peer.hostString}:${result.peer.port}")
        } catch (e: CancellationExceptionLike) {
            runCatching { socket.close() }
            throw e
        } catch (e: Exception) {
            if (!isCurrent(sessionId, generation)) { runCatching { socket.close() }; throw CancellationExceptionLike() }
            runCatching { tunnelInterface?.close() }; tunnelInterface = null
            runCatching { transport?.close() }; transport = null
            running.set(false)
            Log.w(TAG, "Direct P2P connection failed: ${e.message}", e)
            runCatching { socket.close() }
            throw e
        }
    }

    private fun verifyBidirectionalTransport(sessionId: String, generation: Long): Boolean {
        val startedAt = System.currentTimeMillis()
        val transportRef = transport ?: return false
        LinkoConnectionDiagnostics.record(ConnectionStage.HANDSHAKE, "DATA_PLANE_PROBE_STARTED", "Verifying bidirectional encrypted transport", ConnectionSeverity.INFO, sessionId)
        transportRef.sendPing(startedAt)
        val deadline = startedAt + DATA_PLANE_PROBE_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!isCurrent(sessionId, generation) || !running.get()) return false
            if (lastPongReceivedAt.get() >= startedAt) {
                val rtt = System.currentTimeMillis() - startedAt
                LinkoConnectionDiagnostics.record(ConnectionStage.CONNECTED, "DATA_PLANE_PROBE_PASSED", "Bidirectional encrypted transport verified (${rtt} ms)", ConnectionSeverity.SUCCESS, sessionId)
                return true
            }
            try { Thread.sleep(40) } catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); return false }
        }
        LinkoConnectionDiagnostics.record(ConnectionStage.HANDSHAKE, "DATA_PLANE_PROBE_FAILED", "Peer did not acknowledge the encrypted transport probe", ConnectionSeverity.ERROR, sessionId)
        return false
    }

    private fun assertCurrent(sessionId: String, generation: Long) { if (!isCurrent(sessionId, generation)) throw CancellationExceptionLike() }
    private fun isCurrent(sessionId: String, generation: Long): Boolean = sessionGeneration.get() == generation && activeSessionId == sessionId

    private fun failSession(sessionId: String, generation: Long, reason: String, error: Throwable? = null) {
        if (!isCurrent(sessionId, generation)) return
        if (!terminalFailure.compareAndSet(false, true)) return
        if (error != null) Log.e(TAG, "TUNNEL_FAILED session=$sessionId reason=$reason", error) else Log.e(TAG, "TUNNEL_FAILED session=$sessionId reason=$reason")
        runCatching { runBlocking { LinkoDeviceControlApi(applicationContext).transition(sessionId, "failed") } }
            .onFailure { Log.w(TAG, "Failed to publish terminal session state session=$sessionId: ${it.message}") }
        LinkoEngineBridge.reportTunnelState("failed", reason)
        updateForegroundNotification("Connection Failed", reason.replace('_', ' '))
        stopTunnel(reportStoppedState = false, failureReason = reason)
    }

    private fun registerNetworkHandoverCallback(socket: DatagramSocket, sessionId: String, generation: Long) {
        runCatching {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return
            val callback = object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (!isCurrent(sessionId, generation)) return
                    protect(socket); runCatching { transport?.sendPing() }
                }
                override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) { if (isCurrent(sessionId, generation)) protect(socket) }
                override fun onLost(network: android.net.Network) {
                    if (!isCurrent(sessionId, generation)) return
                    LinkoEngineBridge.reportConnectionDiagnostic(ConnectionStage.CONNECTED, "NETWORK_LOST_GRACE", "Default network lost; LINKO is probing for recovery", ConnectionSeverity.WARNING)
                    updateForegroundNotification("Connected — Network interrupted", "LINKO is probing for recovery")
                }
            }
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        }.onFailure { Log.w(TAG, "Could not register network handover callback: ${it.message}") }
    }

    private fun acquireLocks() {
        runCatching {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LINKO:VpnWakeLock")?.apply { setReferenceCounted(false); acquire(24 * 60 * 60 * 1000L) }
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiLock = wifiManager?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "LINKO:VpnWifiLock")?.apply { setReferenceCounted(false); acquire() }
        }.onFailure { Log.w(TAG, "Could not acquire locks: ${it.message}") }
    }

    private fun releaseLocks() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
            if (wifiLock?.isHeld == true) wifiLock?.release(); wifiLock = null
        }
    }

    private fun outboundLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                val packet = ByteArray(MAX_IP_PACKET)
                while (running.get()) {
                    val count = input.read(packet)
                    if (count <= 0) break
                    val raw = packet.copyOf(count)
                    if (router.parse(raw) == null || raw.size > MAX_TUN_PAYLOAD) continue
                    transport?.send(raw, EncryptedDatagramTunnel.PacketType.DATA)
                    bytesUp.addAndGet(raw.size.toLong())
                    LinkoEngineBridge.updateTrafficStats(bytesDown.get(), bytesUp.get())
                }
            }
        } catch (e: Exception) {
            if (running.get() && !terminalFailure.get()) activeSessionId?.let { session -> failSession(session, sessionGeneration.get(), "vpn_outbound_loop_failed", e) }
        }
    }

    private fun inboundLoop() {
        val descriptor = tunnelInterface ?: return
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(descriptor).use { output ->
                while (running.get()) {
                    try {
                        when (val rx = transport?.receive(RECEIVE_TIMEOUT_MS)) {
                            null -> Unit
                            else -> when (rx.type) {
                                EncryptedDatagramTunnel.PacketType.DATA -> {
                                    val packet = rx.payload
                                    if (packet.isNotEmpty() && packet.size <= MAX_IP_PACKET && router.parse(packet) != null) { output.write(packet); output.flush(); bytesDown.addAndGet(packet.size.toLong()); LinkoEngineBridge.updateTrafficStats(bytesDown.get(), bytesUp.get()) }
                                }
                                EncryptedDatagramTunnel.PacketType.PONG -> {
                                    val sentAt = if (rx.payload.size >= 8) ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long else 0L
                                    val rtt = System.currentTimeMillis() - sentAt
                                    lastPongReceivedAt.set(System.currentTimeMillis())
                                    LinkoEngineBridge.updateTrafficStats(bytesDown.get(), bytesUp.get(), rtt.toInt().coerceAtLeast(1))
                                }
                                EncryptedDatagramTunnel.PacketType.PING -> { val sentAt = if (rx.payload.size >= 8) ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long else System.currentTimeMillis(); transport?.sendPong(sentAt) }
                                EncryptedDatagramTunnel.PacketType.CLOSE -> { stopTunnel(); return }
                                else -> Unit
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) { }
                }
            }
        } catch (e: Exception) {
            if (running.get() && !terminalFailure.get()) activeSessionId?.let { session -> failSession(session, sessionGeneration.get(), "vpn_inbound_loop_failed", e) }
        }
    }

    @Synchronized private fun stopTunnel(reportStoppedState: Boolean = !terminalFailure.get(), failureReason: String? = null) {
        val wasRunning = running.getAndSet(false)
        val sessionToDisconnect = activeSessionId
        releaseLocks()
        runCatching { networkCallback?.let { (getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager)?.unregisterNetworkCallback(it) } }
        networkCallback = null
        scheduler?.shutdownNow(); scheduler = null
        recoveryInProgress.set(false)
        runCatching { transport?.sendClose() }; runCatching { transport?.close() }; transport = null
        runCatching { tunnelInterface?.close() }; tunnelInterface = null
        updateForegroundNotification("LINKO", if (failureReason != null) "Connection failed: ${failureReason.replace('_', ' ')}" else if (wasRunning) "Direct tunnel closed" else "Direct connection cancelled")
        if (reportStoppedState) sessionToDisconnect?.takeIf { it.isNotBlank() }?.let { sessionId -> runCatching { runBlocking { LinkoDeviceControlApi(applicationContext).transition(sessionId, "disconnected") } }.onFailure { Log.w(TAG, "Failed to publish disconnected session=$sessionId: ${it.message}") } ; LinkoEngineBridge.reportTunnelState("stopped", if (wasRunning) "Direct tunnel closed" else "Direct connection cancelled") }
        activeSessionId = null
    }

    private fun createChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL_ID, "LINKO VPN", NotificationManager.IMPORTANCE_LOW))
    }

    private fun serviceNotification(title: String, text: String): Notification {
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL_ID) else Notification.Builder(this)
        return builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text).setContentIntent(PendingIntent.getActivity(this, 9001, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)).setOngoing(true).build()
    }

    private fun updateForegroundNotification(title: String, text: String) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) runCatching {
            val notification = serviceNotification(title, text)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED) else startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onRevoke() { activeSessionId?.takeIf { it.isNotBlank() }?.let { failSession(it, sessionGeneration.get(), "vpn_permission_revoked") }; runCatching { super.onRevoke() } }
    override fun onDestroy() { sessionGeneration.incrementAndGet(); stopTunnel(); executor.shutdownNow(); super.onDestroy() }
    private class CancellationExceptionLike : RuntimeException()

    companion object {
        private const val TAG = "LINKO_VPN_SERVICE"
        private const val CHANNEL_ID = "linko_vpn"
        private const val NOTIFICATION_ID = 7002
        const val EXTRA_PEER_HOST = "linko.peer.host"
        const val EXTRA_PEER_PORT = "linko.peer.port"
        const val EXTRA_SESSION_ID = "linko.session.id"
        const val EXTRA_ROLE = "linko.role"
        const val EXTRA_SESSION_KEY = "linko.session.key"
        const val EXTRA_ALLOWED_PACKAGES = "linko.allowed.packages"
        const val ROLE_RECEIVER = "receiver"
        private const val RECEIVE_TIMEOUT_MS = 500
        private const val HEARTBEAT_TIMEOUT_MS = 45_000L
        private const val HEARTBEAT_DEAD_MS = 70_000L
        private const val DATA_PLANE_PROBE_TIMEOUT_MS = 8_000L
        private const val AUTO_RECOVERY_MAX_ATTEMPTS = 4
        private const val AUTO_RECOVERY_INITIAL_DELAY_MS = 1_500L
        private const val AUTO_RECOVERY_MAX_DELAY_MS = 8_000L
        private const val MAX_IP_PACKET = 64 * 1024
        private const val MAX_TUN_PAYLOAD = 32 * 1024
        private const val TUN_MTU = 1280
    }
}