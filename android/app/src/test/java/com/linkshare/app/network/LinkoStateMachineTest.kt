package com.linkshare.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkoStateMachineTest {

    @Test
    fun testFriendshipTransitions() {
        assertEquals(LinkoStateMachine.Friendship.FRIEND, LinkoStateMachine.friendshipFromBackend("friend"))
        assertEquals(LinkoStateMachine.Friendship.FRIEND, LinkoStateMachine.friendshipFromBackend("accepted"))
        assertEquals(LinkoStateMachine.Friendship.OUTGOING_PENDING, LinkoStateMachine.friendshipFromBackend("outgoing_pending"))
        assertEquals(LinkoStateMachine.Friendship.INCOMING_PENDING, LinkoStateMachine.friendshipFromBackend("incoming_pending"))
        assertEquals(LinkoStateMachine.Friendship.DECLINED, LinkoStateMachine.friendshipFromBackend("declined"))
        assertEquals(LinkoStateMachine.Friendship.NONE, LinkoStateMachine.friendshipFromBackend("none"))
        assertEquals(LinkoStateMachine.Friendship.NONE, LinkoStateMachine.friendshipFromBackend(null))
    }

    @Test
    fun testAvailabilityTransitions() {
        assertEquals(LinkoStateMachine.Availability.OFFLINE, LinkoStateMachine.availabilityFromPresence("online", false))
        assertEquals(LinkoStateMachine.Availability.OFFLINE, LinkoStateMachine.availabilityFromPresence("offline", true))
        assertEquals(LinkoStateMachine.Availability.READY, LinkoStateMachine.availabilityFromPresence("ready", true))
        assertEquals(LinkoStateMachine.Availability.SHARING, LinkoStateMachine.availabilityFromPresence("sharing", true))
        assertEquals(LinkoStateMachine.Availability.CONNECTED, LinkoStateMachine.availabilityFromPresence("connected", true))
        assertEquals(LinkoStateMachine.Availability.ONLINE, LinkoStateMachine.availabilityFromPresence("online", true))
    }

    @Test
    fun testConnectionStates() {
        assertEquals(LinkoStateMachine.Connection.REQUESTED, LinkoStateMachine.connectionFromBackend("requested"))
        assertEquals(LinkoStateMachine.Connection.AUTHORIZED, LinkoStateMachine.connectionFromBackend("approved"))
        assertEquals(LinkoStateMachine.Connection.AUTHORIZED, LinkoStateMachine.connectionFromBackend("authorized"))
        assertEquals(LinkoStateMachine.Connection.SIGNALING, LinkoStateMachine.connectionFromBackend("signaling"))
        assertEquals(LinkoStateMachine.Connection.HANDSHAKING, LinkoStateMachine.connectionFromBackend("handshaking"))
        assertEquals(LinkoStateMachine.Connection.TUNNEL_ESTABLISHED, LinkoStateMachine.connectionFromBackend("tunnel_established"))
        assertEquals(LinkoStateMachine.Connection.TRAFFIC_ACTIVE, LinkoStateMachine.connectionFromBackend("traffic_active"))
        assertEquals(LinkoStateMachine.Connection.CONNECTED, LinkoStateMachine.connectionFromBackend("connected"))
        assertEquals(LinkoStateMachine.Connection.DENIED, LinkoStateMachine.connectionFromBackend("denied"))
        assertEquals(LinkoStateMachine.Connection.REVOKED, LinkoStateMachine.connectionFromBackend("revoked"))
        assertEquals(LinkoStateMachine.Connection.EXPIRED, LinkoStateMachine.connectionFromBackend("expired"))
        assertEquals(LinkoStateMachine.Connection.AUTH_FAILED, LinkoStateMachine.connectionFromBackend("auth_failed"))
        assertEquals(LinkoStateMachine.Connection.NETWORK_LOST, LinkoStateMachine.connectionFromBackend("network_lost"))
        assertEquals(LinkoStateMachine.Connection.PROVIDER_OFFLINE, LinkoStateMachine.connectionFromBackend("provider_offline"))
        assertEquals(LinkoStateMachine.Connection.DISCONNECTED, LinkoStateMachine.connectionFromBackend("disconnected"))
    }

    @Test
    fun testCanRequestConnection() {
        assertTrue(LinkoStateMachine.canRequestConnection(LinkoStateMachine.Friendship.FRIEND, LinkoStateMachine.Availability.READY))
        assertTrue(LinkoStateMachine.canRequestConnection(LinkoStateMachine.Friendship.FRIEND, LinkoStateMachine.Availability.ONLINE))
        assertFalse(LinkoStateMachine.canRequestConnection(LinkoStateMachine.Friendship.FRIEND, LinkoStateMachine.Availability.OFFLINE))
        assertFalse(LinkoStateMachine.canRequestConnection(LinkoStateMachine.Friendship.NONE, LinkoStateMachine.Availability.READY))
        assertFalse(LinkoStateMachine.canRequestConnection(LinkoStateMachine.Friendship.OUTGOING_PENDING, LinkoStateMachine.Availability.READY))
    }
}
