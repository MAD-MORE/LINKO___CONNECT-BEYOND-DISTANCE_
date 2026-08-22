package com.linko.connectivity

/** Single runtime lifecycle boundary for the engine/backend connection. */
class LinkoRuntime {
    @Volatile private var started = false

    fun start() { started = true }
    fun stop() { started = false }
    fun isStarted(): Boolean = started
}
