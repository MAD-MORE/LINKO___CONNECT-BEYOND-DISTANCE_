package com.linkshare.app.tunnel

import android.util.Log
import com.linkshare.app.network.LinkoEngineBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Runs the provider side of a LINKO tunnel session.
 * Receives encrypted client IP packets, routes them to the Internet, and returns encrypted responses.
 * The lifecycle callback keeps the service/control plane synchronized when the receiver closes the tunnel.
 */
class ProviderTunnelRunner(
    private val socket: DatagramSocket,
    private val endpoint: InetSocketAddress,
    val sessionId: String,
    sessionKey: ByteArray,
    private val scope: CoroutineScope,
    private val adapter: ProviderTransportAdapter = FullIpProviderTransportAdapter(),
    private val onClosed: (String) -> Unit = {},
) : AutoCloseable {

    private val tunnel = EncryptedDatagramTunnel(
        socket = socket,
        peer = endpoint,
        sessionId = sessionId,
        role = EncryptedDatagramTunnel.Role.PROVIDER,
        sessionKey = sessionKey
    )

    private var receiveJob: Job? = null
    private var drainJob: Job? = null
    private val bytesIn = AtomicLong(0)
    private val bytesOut = AtomicLong(0)
    private val closeNotified = AtomicBoolean(false)

    fun start() {
        if (receiveJob != null) return

        receiveJob = scope.launch(Dispatchers.IO) {
            runCatching { Log.i(TAG, "Provider tunnel loop started for session=$sessionId endpoint=$endpoint") }
            try {
                while (isActive) {
                    try {
                        val rx = tunnel.receive(500) ?: continue
                        when (rx.type) {
                            EncryptedDatagramTunnel.PacketType.DATA -> {
                                val inCount = bytesIn.addAndGet(rx.payload.size.toLong())
                                LinkoEngineBridge.updateTrafficStats(inCount, bytesOut.get())
                                val responses = adapter.forward(rx.payload)
                                for (resp in responses) {
                                    tunnel.send(resp, EncryptedDatagramTunnel.PacketType.DATA)
                                    val outCount = bytesOut.addAndGet(resp.size.toLong())
                                    LinkoEngineBridge.updateTrafficStats(bytesIn.get(), outCount)
                                }
                            }
                            EncryptedDatagramTunnel.PacketType.PING -> {
                                val timestamp = if (rx.payload.size >= 8) {
                                    ByteBuffer.wrap(rx.payload).order(ByteOrder.BIG_ENDIAN).long
                                } else {
                                    System.currentTimeMillis()
                                }
                                tunnel.sendPong(timestamp)
                            }
                            EncryptedDatagramTunnel.PacketType.CLOSE -> {
                                Log.i(TAG, "Received CLOSE signal from client for session=$sessionId")
                                break
                            }
                            else -> Unit
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                        // Poll again while keeping session alive.
                    } catch (e: Exception) {
                        if (isActive) Log.w(TAG, "Provider packet processing error: ${e.message}")
                    }
                }
            } finally {
                notifyClosed("peer_closed")
            }
        }

        drainJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    try {
                        val pending = adapter.drainPending(16)
                        for (packet in pending) {
                            tunnel.send(packet, EncryptedDatagramTunnel.PacketType.DATA)
                            val outCount = bytesOut.addAndGet(packet.size.toLong())
                            LinkoEngineBridge.updateTrafficStats(bytesIn.get(), outCount)
                        }
                        if (pending.isEmpty()) delay(20)
                    } catch (_: Exception) {
                        delay(50)
                    }
                }
            } finally {
                // The receive loop owns terminal close notification; this coroutine is only a drain worker.
            }
        }
    }

    private fun notifyClosed(reason: String) {
        if (!closeNotified.compareAndSet(false, true)) return
        runCatching { Log.i(TAG, "Provider tunnel closed session=$sessionId reason=$reason") }
        runCatching { onClosed(reason) }
    }

    fun stop() {
        receiveJob?.cancel()
        drainJob?.cancel()
        receiveJob = null
        drainJob = null
        adapter.close()
        runCatching { tunnel.sendClose() }
        tunnel.close()
        runCatching { socket.close() }
        notifyClosed("provider_stopped")
        runCatching { Log.i(TAG, "Provider tunnel stopped for session=$sessionId") }
    }

    override fun close() = stop()

    companion object {
        private const val TAG = "LINKO_PROVIDER_RUNNER"
    }
}
