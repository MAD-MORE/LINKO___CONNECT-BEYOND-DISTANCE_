package com.linkshare.app.network

/**
 * Runtime configuration for the LINKO Android client.
 *
 * The APK never contains authentication secrets. The control-plane URL is a
 * build-time value so production builds can point at the deployed backend.
 */
object LinkoRuntimeConfig {
    const val controlPlaneUrl: String = BuildConfig.LINKO_CONTROL_PLANE_URL

    fun isConfigured(): Boolean = controlPlaneUrl.startsWith("https://") && controlPlaneUrl.length > 8
}
