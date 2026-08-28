package com.linkshare.app.network

import com.linkshare.app.BuildConfig

/** Runtime configuration for the LINKO Android client. Uses Supabase directly as the official control plane. */
object LinkoRuntimeConfig {
    const val controlPlaneUrl: String = BuildConfig.LINKO_SUPABASE_URL

    fun isConfigured(): Boolean =
        controlPlaneUrl.startsWith("https://") && controlPlaneUrl.length > 8
}
