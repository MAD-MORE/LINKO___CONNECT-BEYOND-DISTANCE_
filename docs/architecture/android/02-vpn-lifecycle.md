# VpnService Lifecycle — Linko Android

## Overview

Linko uses Android's `VpnService` API to intercept the Receiver's network traffic and route it through the Provider's connection. This document describes the full lifecycle state machine for the VPN layer.

---

## VpnService State Machine

```
                    ┌─────────────────────┐
                    │        IDLE         │
                    │  (App launched,     │
                    │   not connected)    │
                    └──────────┬──────────┘
                               │ User taps "Connect"
                               │ Session approved
                               ▼
                    ┌─────────────────────┐
                    │    PREPARING_VPN    │
                    │  VpnService.prepare │
                    │  (shows OS dialog)  │
                    └──────────┬──────────┘
                               │ User grants VPN permission
                               ▼
                    ┌─────────────────────┐
                    │    VPN_STARTING     │
                    │  Build VPN iface    │
                    │  Set routes/DNS     │
                    └──────────┬──────────┘
                               │ VPN interface created
                               ▼
                    ┌─────────────────────┐
                    │    TUNNEL_CONNECT   │
                    │  Connect UDP socket │
                    │  Authenticate relay │
                    └──────────┬──────────┘
                               │ Tunnel established
                               ▼
                    ┌─────────────────────┐
                    │     CONNECTED       │◄─────────┐
                    │  Traffic flowing    │          │
                    │  Heartbeat active   │ Network  │
                    └────┬──────────┬─────┘ restored │
                         │          │                │
                 Network │          │ Provider       │
                   loss  │          │ disconnects    │
                         ▼          ▼                │
              ┌──────────────┐   ┌──────────────┐   │
              │ RECONNECTING │   │ DISCONNECTED │   │
              │ Retry tunnel │   │ VPN torn down │   │
              │ up to 3×     │──►│ (final state) │   │
              └──────────────┘   └──────────────┘   │
                    │                                │
                    └────────────────────────────────┘
                         (reconnect succeeds)
```

---

## VPN Interface Configuration

When `LinkShareVpnService` starts, it configures the VPN interface:

```kotlin
// Receiver VPN interface setup
VpnService.Builder()
    .setSession("Linko")
    .addAddress("10.0.0.2", 32)          // Receiver's VPN IP
    .addRoute("0.0.0.0", 0)              // Route ALL traffic through VPN
    .addDnsServer("1.1.1.1")             // Cloudflare DNS via tunnel
    .addDnsServer("8.8.8.8")             // Google DNS fallback
    .setMtu(1400)                         // MTU accounts for tunnel overhead
    .establish()                          // Returns ParcelFileDescriptor
```

The Provider is assigned `10.0.0.1/32` as its tunnel endpoint. All Receiver traffic is captured and written to the VPN file descriptor.

---

## Foreground Service Requirements

Android 8.0+ (API 26+, which is Linko's minSdk) requires VPN services running in the background to be foreground services with a persistent notification.

### Notification specification

```
Title: "Linko — Connected"
Text:  "Sharing via [Provider Name] • 12.4 MB used"
Icon:  ic_linko_connected (green pulse icon)
Actions:
  [Disconnect] → sends DISCONNECT intent to service
```

The notification is updated every 10 seconds with current usage data.

---

## Permission Requirements

| Permission | When required | Manifest |
|---|---|---|
| `BIND_VPN_SERVICE` | Always (VpnService) | `AndroidManifest.xml` |
| `FOREGROUND_SERVICE` | Always | `AndroidManifest.xml` |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Android 14+ | `AndroidManifest.xml` |
| `POST_NOTIFICATIONS` | Android 13+ | Requested at runtime |
| `INTERNET` | Always | `AndroidManifest.xml` |
| `RECEIVE_BOOT_COMPLETED` | Auto-reconnect (future) | Deferred to post-MVP |

---

## Battery Optimization

Linko requests exemption from battery optimization for the Provider service (not for Receiver by default, since users expect VPN battery drain):

```kotlin
// On Provider setup screen
val pm = getSystemService(PowerManager::class.java)
if (!pm.isIgnoringBatteryOptimizations(packageName)) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.parse("package:$packageName"))
    startActivity(intent)
}
```

---

## Network Callback Handling

`LinkoStateMachine` registers a `ConnectivityManager.NetworkCallback` to detect network changes:

```kotlin
networkCallback = object : ConnectivityManager.NetworkCallback() {
    override fun onAvailable(network: Network) {
        // Network restored → trigger reconnect if in RECONNECTING state
        stateMachine.onNetworkAvailable()
    }
    override fun onLost(network: Network) {
        // Network lost → move to RECONNECTING state
        stateMachine.onNetworkLost()
    }
    override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
        // Network type changed (e.g. WiFi → mobile data) → re-evaluate direct path
        stateMachine.onNetworkCapabilitiesChanged(caps)
    }
}
```

---

## Clean Shutdown Sequence

When the user disconnects or the Provider revokes:

1. `LinkShareVpnService.onDestroy()` called (or DISCONNECT intent received)
2. `TunnelCoordinator.shutdown()` signals tunnel loops to stop
3. `FullIpTunnelEngine` drains pending packets and closes UDP socket
4. `VpnService.Builder` interface is closed (file descriptor closed)
5. Android tears down the VPN network interface
6. `LinkoStateMachine` transitions to `DISCONNECTED`
7. `LinkoRuntime` POSTs session transition `{"state":"disconnected"}` to control plane
8. Foreground notification is cancelled

Total shutdown time target: **< 1 second**.
