package com.linkshare.app.network

/** UI bridge initialized once from the authenticated LINKO app session. */
object LinkoFriendsApiHolder {
    lateinit var api: LinkoFriendsApi
    var selected: FriendSearchResult? = null
}
