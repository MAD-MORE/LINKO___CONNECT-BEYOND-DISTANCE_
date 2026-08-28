# Android Module Map — Linko

## Package Structure

```
com.linkshare.app/
├── auth/                    ← Authentication flows
│   ├── LinkoAuth.kt         ← Sign-up, sign-in, token refresh via Supabase
│   ├── LinkoDeviceIdentity.kt  ← Permanent device ID (EncryptedSharedPrefs)
│   ├── LinkoRequestKey.kt   ← Per-session temporary request key
│   ├── LinkoStartupCache.kt ← Cached auth state for fast app startup
│   └── PasswordRecovery.kt  ← Password reset flow
│
├── data/                    ← Data layer (being replaced by live repository)
│   ├── MockLinkShareRepository.kt  ← [DEPRECATED] Mock data (replaced by LinkoLiveRepository)
│   └── LinkoLiveRepository.kt      ← [NEW] Live API-backed repository
│
├── model/                   ← Domain models
│   └── LinkShareModels.kt   ← Session, Device, Friend, UsageRecord data classes
│
├── network/                 ← Networking + session orchestration
│   ├── LinkoRuntime.kt      ← Main orchestrator: auth, device, session, tunnel lifecycle
│   ├── LinkoRuntimeConfig.kt  ← Server URLs, timeouts, feature flags
│   ├── LinkoControlPlaneApi.kt  ← HTTP client for control plane endpoints
│   ├── LinkoDeviceControlApi.kt ← Device registration and management API
│   ├── LinkoDeviceRegistrar.kt  ← One-time device registration on app install
│   ├── LinkoFriendsApi.kt   ← Friend search, add, remove, list
│   ├── LinkoFriendsApiHolder.kt ← Singleton holder for friends API client
│   ├── LinkoProfileApi.kt   ← User profile fetch and update
│   ├── LinkoPresenceManager.kt  ← Provider online/offline state tracking
│   ├── LinkoRealtimeManager.kt  ← Supabase Realtime WebSocket subscription
│   ├── LinkoSignalingClient.kt  ← Polls signaling endpoint (offer/answer/ICE)
│   ├── LinkoStateMachine.kt ← Session state machine (IDLE→REQUESTING→APPROVED→CONNECTED)
│   ├── LinkoEngineBridge.kt ← Bridge between state machine and tunnel engine
│   ├── LinkShareApi.kt      ← Legacy API client (being superseded)
│   └── FriendSearchResult.kt  ← Search result data class
│
├── provider/                ← Provider-side service
│   └── LinkoProviderService.kt  ← Android foreground Service for Provider tunnel loop
│
├── security/                ← [NEW] Security utilities
│   ├── LinkoKeyManager.kt   ← Android Keystore key generation and storage
│   └── LinkoSecurePrefs.kt  ← EncryptedSharedPreferences wrapper
│
├── tunnel/                  ← Data-plane tunnel engine
│   ├── FullIpTunnelEngine.kt  ← Top-level tunnel orchestrator
│   ├── EncryptedDatagramTunnel.kt  ← AES-GCM UDP datagram encrypt/decrypt
│   ├── TunnelCoordinator.kt ← Coordinates VpnService ↔ tunnel engine
│   ├── IpPacketClassifier.kt  ← Classify IP packets (TCP/UDP/ICMP)
│   ├── IpPacketRouter.kt    ← Route packets to appropriate forwarder
│   ├── IpFlowRouter.kt      ← Flow-based routing (5-tuple tracking)
│   ├── TcpFlowTable.kt      ← Active TCP connection tracking table
│   ├── ProviderTunnelRunner.kt  ← Provider-side tunnel read/write loop
│   ├── ProviderFullIpSession.kt ← Provider full-IP session state
│   ├── ProviderPacketForwarder.kt  ← Base packet forwarding interface
│   ├── ProviderIpPacketForwarder.kt ← IP-level forwarding
│   ├── ProviderTcpPacketForwarder.kt ← TCP-specific forwarding
│   ├── ProviderUdpPacketForwarder.kt ← UDP-specific forwarding
│   ├── ProviderTransportAdapter.kt  ← Abstraction over direct/relay transport
│   └── ProviderSocks5Server.kt ← SOCKS5 proxy for Provider-side traffic
│
├── ui/                      ← Presentation layer (Jetpack Compose)
│   ├── components/
│   │   ├── Components.kt    ← Shared UI components (buttons, cards, inputs)
│   │   ├── LinkoRealtimeOverlay.kt ← Live connection status overlay
│   │   └── Ring.kt          ← Animated connection ring component
│   ├── screens/
│   │   ├── LinkoApp.kt      ← Root Compose app + navigation host
│   │   ├── AuthScreens.kt   ← Sign-in screen
│   │   ├── SignUpScreen.kt  ← Sign-up screen
│   │   ├── OtpScreens.kt    ← OTP verification screens
│   │   ├── ForgotPasswordScreen.kt ← Forgot password
│   │   ├── PasswordResetScreen.kt  ← Password reset
│   │   ├── OnboardingScreens.kt  ← First-run onboarding
│   │   ├── FriendsScreens.kt  ← Friends list, add friend
│   │   ├── LiveFriendsScreen.kt ← Real-time friends online view
│   │   ├── ProviderScreens.kt  ← Provider mode screens
│   │   ├── EngineScreens.kt   ← Tunnel engine status screens
│   │   ├── ConnectionStatusScreen.kt ← Active session status
│   │   ├── SettingsScreens.kt  ← App settings
│   │   ├── RealAccountProfileScreen.kt ← User profile
│   │   ├── RealFriendProfileScreen.kt  ← Friend profile
│   │   └── RealReconnectingScreen.kt   ← Reconnecting state screen
│   └── theme/
│       ├── Color.kt          ← Color tokens (dark palette)
│       ├── Theme.kt          ← MaterialTheme configuration
│       └── Type.kt           ← Typography scale
│
├── viewmodel/               ← UI state management
│   └── LinkShareViewModel.kt ← Main ViewModel: holds UI state, delegates to LinkoRuntime
│
└── vpn/                     ← Android VPN layer
    └── LinkShareVpnService.kt ← Android VpnService: creates VPN interface, starts tunnel
```

---

## Data Flow Diagram

```
User Action (UI)
      │
      ▼
LinkShareViewModel
      │ state updates
      ▼
LinkoRuntime (orchestrator)
      │
      ├── LinkoAuth ──────────────────────► Supabase Auth
      ├── LinkoDeviceRegistrar ────────────► Control Plane API
      ├── LinkoFriendsApi ─────────────────► Supabase DB (friends)
      ├── LinkoPresenceManager ────────────► Supabase Realtime
      ├── LinkoSignalingClient ────────────► Control Plane Signaling
      └── LinkoEngineBridge
                │
                ▼
        LinkoStateMachine
                │
    ┌───────────┴────────────┐
    ▼                        ▼
LinkShareVpnService    LinkoProviderService
(Receiver side)        (Provider side)
    │                        │
    ▼                        ▼
FullIpTunnelEngine     ProviderTunnelRunner
    │                        │
    ▼                        ▼
EncryptedDatagramTunnel ◄──► EncryptedDatagramTunnel
         │                         │
         ▼                         ▼
    Direct UDP             OR    Relay UDP
    (peer-to-peer)               (via Fly.io relay)
```

---

## Module Dependencies

| Module | Depends on | Must NOT depend on |
|---|---|---|
| `auth/` | Android Keystore, Supabase SDK | `tunnel/`, `vpn/` |
| `network/` | `auth/`, `model/` | `vpn/`, `tunnel/` |
| `tunnel/` | `network/` (for keys) | `ui/` |
| `vpn/` | `tunnel/`, `network/` | `ui/` |
| `provider/` | `tunnel/`, `network/` | `ui/` |
| `ui/` | `viewmodel/`, `model/` | `tunnel/`, `vpn/` (direct) |
| `viewmodel/` | `network/`, `model/` | `tunnel/`, `vpn/` (direct) |
| `security/` | Android Keystore | Everything else |

---

## Key Lifecycle Responsibilities

### App Startup
1. `MainActivity` creates `LinkShareViewModel`
2. `LinkoStartupCache` checks for cached auth token
3. If authenticated: navigate to main screen; else: navigate to auth
4. `LinkoDeviceRegistrar` registers device on first run

### Session Establishment (Receiver)
1. User selects friend → taps "Connect"
2. `ViewModel` → `LinkoRuntime.requestConnection(friendUserId)`
3. `LinkoRuntime` → POST `/v1/sessions` → session created in `PENDING` state
4. `LinkoSignalingClient` starts polling every 2s
5. Provider approves → session transitions to `APPROVED`
6. `LinkoRuntime` → GET `/v1/sessions/:id/tunnel` → receives endpoint + key
7. `LinkoEngineBridge` starts `LinkShareVpnService`
8. `FullIpTunnelEngine` connects to relay/direct endpoint with session key
9. Session moves to `CONNECTED`; UI shows connection screen

### Session Establishment (Provider)
1. `LinkoProviderService` runs as foreground service
2. Polls GET `/v1/provider/requests` every 10s
3. Incoming request shown as notification → Provider taps "Approve"
4. POST `/v1/sessions/:id/transition` `{"state":"approved"}`
5. `ProviderTunnelRunner` starts, connects to same relay/direct endpoint
6. Forwards Receiver packets through Provider's network connection
