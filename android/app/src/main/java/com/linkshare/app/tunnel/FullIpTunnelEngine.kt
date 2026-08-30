package com.linkshare.app.tunnel

import android.net.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.nio.ByteBuffer

/**
 * LINKO Full-IP Tunnel Engine (Receiver side).
 *
 * Orchestrates:
 * 1. Reading raw IP packets from VPN interface
 * 2. Classifying packets (TCP/UDP/ICMP)
 * 3. Routing to appropriate forwarders
 * 4. Encrypting packets via EncryptedDatagramTunnel
 * 5. Forwarding to Provider via UDP (direct or relay)
 *
 * Architecture:
 * VpnInterface
 *    │
 *    ▼
 * IpPacketReader (FileDescriptor)
 *    │
 *    ▼
 * IpPacketClassifier
 *    │
 *    ├─→ IpFlowRouter (5-tuple tracking)
 *    │
 *    ├─→ TcpFlowTable (connection state)
 *    │
 *    └─→ EncryptedDatagramTunnel (encrypted UDP send)
 *          │
 *          ▼
 *    Direct UDP or Relay UDP
 */
class FullIpTunnelEngine(
    private val vpnInterface: ParcelFileDescriptor,
    private val providerAddress: InetSocketAddress,
    private val sessionId: String,
    private val sessionKey: ByteArray,
    private val relayUrl: String? = null
) : AutoCloseable {

    private val TAG = "LINKO-TunnelEngine"
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val packetClassifier = IpPacketClassifier()
    private val flowRouter = IpFlowRouter()
    private val tcpFlowTable = TcpFlowTable()

    private lateinit var encryptedTunnel: EncryptedDatagramTunnel
    private var tunnelJob: kotlinx.coroutines.Job? = null

    fun start() {
        Log.d(TAG, "FullIpTunnelEngine starting")

        try {
            // Create the encrypted datagram tunnel
            val socket = DatagramSocket()
            encryptedTunnel = EncryptedDatagramTunnel(
                socket = socket,
                peer = providerAddress,
                sessionId = sessionId,
                role = EncryptedDatagramTunnel.Role.RECEIVER,
                sessionKey = sessionKey
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize encrypted tunnel: ${e.message}", e)
            throw e
        }

        // Start the main packet read/write loop
        tunnelJob = engineScope.launch {
            readPacketsLoop()
        }

        Log.d(TAG, "FullIpTunnelEngine started")
    }

    private suspend fun readPacketsLoop() {
        val vpnFileDescriptor = vpnInterface.fileDescriptor
        val buffer = ByteArray(32 * 1024) // 32 KB MTU

        while (engineScope.isActive) {
            try {
                // Read raw IP packet from VPN interface
                val bytesRead = android.os.ParcelFileDescriptor.AutoCloseInputStream(vpnInterface).read(buffer)
                if (bytesRead <= 0) {
                    Log.d(TAG, "No more packets from VPN interface")
                    break
                }

                val packet = buffer.copyOfRange(0, bytesRead)

                // Classify the packet
                val classification = packetClassifier.classify(packet)
                if (classification == null) {
                    Log.w(TAG, "Failed to classify packet (${bytesRead}B)")
                    continue
                }

                Log.d(TAG, "Received ${classification.protocol} packet from ${classification.srcAddr}:${classification.srcPort} to ${classification.dstAddr}:${classification.dstPort}")

                // Track the flow
                val flowKey = classification.toFlowKey()
                flowRouter.recordFlow(flowKey, classification)

                // Handle TCP state tracking
                if (classification.protocol == "TCP") {
                    tcpFlowTable.updateFlow(flowKey, packet)
                }

                // Send encrypted packet to Provider
                encryptedTunnel.send(packet, EncryptedDatagramTunnel.PacketType.DATA)

            } catch (e: Exception) {
                if (engineScope.isActive) {
                    Log.e(TAG, "Error in packet read loop: ${e.message}", e)
                }
            }
        }

        Log.d(TAG, "Packet read loop terminated")
    }

    fun receiveAndForwardResponse() {
        engineScope.launch {
            while (engineScope.isActive) {
                try {
                    val receivedPacket = encryptedTunnel.receive(500) ?: continue

                    if (receivedPacket.type != EncryptedDatagramTunnel.PacketType.DATA) {
                        Log.d(TAG, "Received non-data packet: ${receivedPacket.type}")
                        continue
                    }

                    // Forward response packet back to the VPN interface
                    // (This would write to vpnInterface.fileDescriptor)
                    Log.d(TAG, "Forwarding response packet (${receivedPacket.payload.size}B)")

                } catch (e: Exception) {
                    if (engineScope.isActive) {
                        Log.e(TAG, "Error receiving response: ${e.message}")
                    }
                }
            }
        }
    }

    override fun close() {
        Log.d(TAG, "Closing FullIpTunnelEngine")
        tunnelJob?.cancel()
        if (::encryptedTunnel.isInitialized) {
            encryptedTunnel.close()
        }
        engineScope.launch {
            engineScope.cancel()
        }
    }
}

data class PacketClassification(
    val protocol: String, // TCP, UDP, ICMP, etc.
    val srcAddr: String,
    val srcPort: Int,
    val dstAddr: String,
    val dstPort: Int,
    val ttl: Int,
    val payload: ByteArray
) {
    fun toFlowKey(): String = "$protocol:$srcAddr:$srcPort->$dstAddr:$dstPort"
}

class IpPacketClassifier {
    fun classify(packet: ByteArray): PacketClassification? {
        if (packet.size < 20) return null

        val buffer = ByteBuffer.wrap(packet)
        val versionAndHeaderLen = buffer.get().toInt() and 0xFF
        val version = (versionAndHeaderLen shr 4) and 0x0F
        val headerLen = (versionAndHeaderLen and 0x0F) * 4

        if (version != 4) return null // Only IPv4 for now

        val dscp = buffer.get().toInt() and 0xFF
        val totalLen = (buffer.short.toInt() and 0xFFFF)
        val identification = buffer.short
        val flagsAndOffset = buffer.short
        val ttl = buffer.get().toInt() and 0xFF
        val protocol = buffer.get().toInt() and 0xFF
        val checksum = buffer.short

        val srcAddr = readIpAddress(buffer)
        val dstAddr = readIpAddress(buffer)

        val protocolName = when (protocol) {
            6 -> "TCP"
            17 -> "UDP"
            1 -> "ICMP"
            else -> "OTHER"
        }

        var srcPort = 0
        var dstPort = 0

        if (protocol == 6 || protocol == 17) {
            buffer.position(headerLen)
            if (packet.size >= headerLen + 4) {
                srcPort = (buffer.short.toInt() and 0xFFFF)
                dstPort = (buffer.short.toInt() and 0xFFFF)
            }
        }

        val payload = packet.copyOfRange(headerLen, packet.size)

        return PacketClassification(
            protocol = protocolName,
            srcAddr = srcAddr,
            srcPort = srcPort,
            dstAddr = dstAddr,
            dstPort = dstPort,
            ttl = ttl,
            payload = payload
        )
    }

    private fun readIpAddress(buffer: ByteBuffer): String {
        val bytes = ByteArray(4)
        buffer.get(bytes)
        return bytes.joinToString(".") { (it.toInt() and 0xFF).toString() }
    }
}

class IpFlowRouter {
    private val activeFlows = mutableMapOf<String, PacketClassification>()

    fun recordFlow(key: String, classification: PacketClassification) {
        activeFlows[key] = classification
    }

    fun getFlow(key: String): PacketClassification? = activeFlows[key]

    fun closeFlow(key: String) {
        activeFlows.remove(key)
    }
}

class TcpFlowTable {
    private val tcpFlows = mutableMapOf<String, TcpFlowState>()

    data class TcpFlowState(
        val key: String,
        var state: String, // ESTABLISHED, FIN_WAIT, CLOSED, etc.
        var lastSeq: Long = 0,
        var lastAck: Long = 0
    )

    fun updateFlow(key: String, packet: ByteArray) {
        val flags = extractTcpFlags(packet)
        val state = when {
            flags.contains("SYN") && !flags.contains("ACK") -> "SYN_SENT"
            flags.contains("SYN") && flags.contains("ACK") -> "ESTABLISHED"
            flags.contains("FIN") -> "FIN_WAIT"
            flags.contains("RST") -> "CLOSED"
            else -> "ESTABLISHED"
        }
        tcpFlows[key] = TcpFlowState(key, state)
    }

    private fun extractTcpFlags(packet: ByteArray): List<String> {
        // Simplified: extract TCP flags byte from packet
        val flags = mutableListOf<String>()
        if (packet.size >= 33) {
            val tcpFlags = packet[33].toInt() and 0xFF
            if ((tcpFlags and 0x02) != 0) flags.add("SYN")
            if ((tcpFlags and 0x10) != 0) flags.add("ACK")
            if ((tcpFlags and 0x01) != 0) flags.add("FIN")
            if ((tcpFlags and 0x04) != 0) flags.add("RST")
        }
        return flags
    }
}
