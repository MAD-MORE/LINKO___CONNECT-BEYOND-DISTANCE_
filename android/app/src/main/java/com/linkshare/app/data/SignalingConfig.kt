package com.linkshare.app.data

object SignalingConfig {
    /** Production must provide an HTTPS signaling host through build configuration or secure runtime configuration. */
    const val BASE_URL: String = "https://REPLACE_WITH_LINKO_SIGNALING_HOST"

    /** Development-only bearer token. Never commit a production credential here. */
    const val API_TOKEN: String = ""
}
