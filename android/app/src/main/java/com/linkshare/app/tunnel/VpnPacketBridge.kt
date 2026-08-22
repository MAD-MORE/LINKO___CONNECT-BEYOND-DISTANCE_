package com.linkshare.app.tunnel

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Bridges packets between Android's TUN interface and an authenticated transport.
 * The transport implementation is injected; plaintext packets are never emitted
 * to a network socket by this class.
 */
class VpnPacketBridge(
    private val input: FileInputStream,
    private val output: FileOutputStream,
    private val transport: PacketTransport,
    private val cipher: AuthenticatedPacketCipher
) {
    @Volatile private var running = false

    fun start() {
        check(!running)
        running = true
        Thread({ readLoop() }, "linko-vpn-tun").start()
    }

    fun stop() { running = false }

    private fun readLoop() {
        val buffer = ByteArray(32767)
        try {
            while (running) {
                val count = input.read(buffer)
                if (count <= 0) continue
                val packet = buffer.copyOf(count)
                transport.send(cipher.encrypt(packet))
            }
        } catch (_: IOException) {
            running = false
        }
    }

    fun receive(frame: ByteArray) {
        if (!running) return
        val packet = cipher.decrypt(frame)
        output.write(packet)
        output.flush()
    }
}

fun interface PacketTransport {
    fun send(encryptedFrame: ByteArray)
}
