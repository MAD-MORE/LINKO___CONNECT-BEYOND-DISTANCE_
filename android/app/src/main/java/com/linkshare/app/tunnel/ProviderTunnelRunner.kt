package com.linkshare.app.tunnel

import android.util.Log
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

/**
 * Runs the provider side of a LINKO tunnel session.
 * Receives encrypted client IP packets, routes them to the Internet, and returns encrypted responses.
 */
class ProviderTunnelRunner(
    private val socket: DatagramSocket,
    private val endpoint: InetSocketAddress,
    val sessionId: String,
    sessionKey: ByteArray,
    private val scope: CoroutineScope,
    private val adapter: ProviderTransportAdapter = FullIpProviderTransportAdapter()
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

    fun start() {
        if (receiveJob != null) return

        receiveJob = scope.launch(Dispatchers.IO) {
            runCatching { Log.i(TAG, "Provider tunnel loop started for session=$sessionId endpoint=$endpoint") }
            while (isActive) {
                try {
                    val rx = tunnel.receive(500) ?: continue
                    when (rx.type) {
                        EncryptedDatagramTunnel.PacketType.DATA -> {
                            val responses = adapter.forward(rx.payload)
                            for (resp in responses) {
                                tunnel.send(resp, EncryptedDatagramTunnel.PacketType.DATA)
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
                            runCatching { Log.i(TAG, "Received CLOSE signal from client for session=$sessionId") }
                            break
                        }
                        else -> Unit
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    // Poll again while keeping session alive.
                } catch (e: Exception) {
                    runCatching { Log.w(TAG, "Provider packet processing error: ${e.message}") }
                }
            }
        }

        drainJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val pending = adapter.drainPending(16)
                    for (packet in pending) {
                        tunnel.send(packet, EncryptedDatagramTunnel.PacketType.DATA)
                    }
                    if (pending.isEmpty()) {
                        delay(20)
                    }
                } catch (_: Exception) {
                    delay(50)
                }
            }
        }
    }

    fun stop() {
        receiveJob?.cancel()
        drainJob?.cancel()
        receiveJob = null
        drainJob = null
        adapter.close()
        tunnel.close()
        runCatching { Log.i(TAG, "Provider tunnel stopped for session=$sessionId") }
    }

    override fun close() = stop()

    companion object {
        private const val TAG = "LINKO_PROVIDER_RUNNER"
    }
}
