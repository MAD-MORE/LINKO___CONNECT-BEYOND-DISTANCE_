package com.linkshare.app.tunnel

import java.io.FileDescriptor

/** Full-IP data-plane engine boundary. */
interface FullIpTunnelEngine : AutoCloseable {
    fun start(tunFd: FileDescriptor, socksHost: String, socksPort: Int)
    fun isRunning(): Boolean
    override fun close()
}

/** Adapter for the embedded userspace HevSocks5Tunnel engine. */
class HevFullIpTunnelEngine : FullIpTunnelEngine {
    private var tunnel: Any? = null

    override fun start(tunFd: FileDescriptor, socksHost: String, socksPort: Int) {
        require(socksHost.isNotBlank()) { "SOCKS5 host is required" }
        require(socksPort in 1..65535) { "SOCKS5 port is invalid" }
        check(tunnel == null) { "full_ip_tunnel_already_started" }

        val tunnelClass = loadFirst(
            "cc.hev.socks5.tunnel.HevSocks5Tunnel",
            "com.zaneschepke.hevtunnel.HevSocks5Tunnel"
        ) ?: error("HevSocks5Tunnel library is unavailable")
        val builderClass = loadFirst(
            "cc.hev.socks5.tunnel.TunnelConfig\$Builder",
            "com.zaneschepke.hevtunnel.TunnelConfig\$Builder"
        ) ?: error("Hev TunnelConfig builder is unavailable")

        val builder = builderClass.getDeclaredConstructor().newInstance()
        invoke(builder, "setSocks5Address", String::class.java, socksHost)
        invoke(builder, "setSocks5Port", Int::class.javaPrimitiveType!!, socksPort)
        invokeIfPresent(builder, "setTunMtu", Int::class.javaPrimitiveType!!, 1500)
        val config = invoke(builder, "build") ?: error("Hev TunnelConfig build returned null")

        val instance = tunnelClass.getDeclaredConstructor().newInstance()
        invoke(instance, "startAsync", config::class.java, FileDescriptor::class.java, config, tunFd)
        tunnel = instance
    }

    override fun isRunning(): Boolean = tunnel?.let { runCatching { invoke(it, "isRunning") as Boolean }.getOrDefault(true) } ?: false

    override fun close() {
        tunnel?.let { runCatching { invoke(it, "stop") } }
        tunnel = null
    }

    private fun loadFirst(vararg names: String): Class<*>? = names.firstNotNullOfOrNull { runCatching { Class.forName(it) }.getOrNull() }

    private fun invoke(target: Any, name: String, vararg args: Any?): Any? {
        val method = target.javaClass.methods.firstOrNull { candidate ->
            candidate.name == name && candidate.parameterTypes.size == args.size &&
                candidate.parameterTypes.withIndex().all { (index, type) ->
                    val value = args[index]
                    value == null || box(type).isAssignableFrom(value.javaClass)
                }
        } ?: error("Hev method not found: $name")
        return method.invoke(target, *args)
    }

    private fun invokeIfPresent(target: Any, name: String, vararg args: Any?) { runCatching { invoke(target, name, *args) } }

    private fun box(type: Class<*>): Class<*> = when (type) {
        java.lang.Integer.TYPE -> Integer::class.java
        java.lang.Long.TYPE -> Long::class.java
        java.lang.Boolean.TYPE -> Boolean::class.java
        java.lang.Float.TYPE -> Float::class.java
        java.lang.Double.TYPE -> Double::class.java
        else -> type
    }
}
