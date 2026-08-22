package com.linko

/** Central runtime connectivity entry point. */
object LinkoConnectivity {
    fun backendBaseUrl(): String = BuildConfig.LINKO_BACKEND_URL
}
