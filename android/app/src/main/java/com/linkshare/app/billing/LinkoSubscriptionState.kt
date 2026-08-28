package com.linkshare.app.billing

/**
 * Represents the user's current Linko subscription state.
 */
enum class LinkoSubscriptionState {
    /** Free tier: 1 GB relay/month */
    Free,

    /** Pro tier: 10 GB relay/month ($4.99/month) */
    Pro,

    /** Unlimited tier: Unlimited relay ($9.99/month) */
    Unlimited,

    /** State is being loaded (e.g. querying Play Billing) */
    Loading,

    /** Purchase expired or cancelled */
    Expired;

    val displayName: String get() = when (this) {
        Free -> "Free"
        Pro -> "Pro"
        Unlimited -> "Unlimited"
        Loading -> "Loading..."
        Expired -> "Expired"
    }

    val monthlyRelayQuotaBytes: Long get() = when (this) {
        Free -> 1_073_741_824L       // 1 GB
        Pro -> 10_737_418_240L       // 10 GB
        Unlimited -> Long.MAX_VALUE  // Unlimited
        Loading -> 0L
        Expired -> 0L
    }

    val isPaid: Boolean get() = this == Pro || this == Unlimited
}
