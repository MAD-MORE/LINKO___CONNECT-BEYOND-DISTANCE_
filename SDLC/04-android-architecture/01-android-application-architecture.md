# Phase 4.1 — Android Application Architecture

## Status

**COMPLETE — APPROVED BY PROJECT OWNER / UNLOCKED EXECUTION**

## Purpose

Define the Android application's architectural structure, module boundaries, lifecycle responsibilities, security boundaries, and communication paths before implementation begins.

---

## 1. Architecture Model

Linko shall use a modular Android architecture with clear separation between presentation, application orchestration, domain logic, networking/control-plane communication, VPN/tunnel data-plane functionality, persistence, and platform integration.

### Proposed high-level structure

```text
linko-android/
├── app/                    # Application composition and entry points
├── core/
│   ├── model/              # Shared domain models
│   ├── security/           # Crypto/key/security abstractions
│   ├── networking/         # HTTP/signaling transport abstractions
│   ├── storage/            # Local persistence abstractions
│   ├── common/             # Shared utilities and result types
│   └── testing/            # Shared test infrastructure
├── feature/
│   ├── auth/               # Authentication/account flows
│   ├── connections/        # Provider/Receiver discovery and sessions
│   ├── provider/           # Provider sharing controls
│   ├── receiver/           # Receiver connection controls
│   ├── settings/           # User/device settings
│   └── diagnostics/        # User-facing diagnostics
├── tunnel/
│   ├── vpn/                # Android VPNService integration
│   ├── engine/             # Packet/session forwarding engine
│   ├── transport/          # Direct/relay transport abstraction
│   └── routing/            # Route/DNS/network policy
└── platform/
    ├── android/            # Android-specific adapters
    └── notifications/      # Foreground service/status notifications
```

Technology choices such as exact UI framework, DI library, persistence implementation, and transport library remain implementation decisions subject to later technical decision records.

---

# 2. Application Shell

### AND-001 — Single Application Composition Root
**Priority:** P0

The application shall have a clearly defined composition root responsible for constructing major dependencies and application services.

### AND-002 — Environment Configuration
**Priority:** P0

Development, staging, and production configuration shall be separated and shall not embed production secrets in source code.

### AND-003 — Feature Isolation
**Priority:** P0

Features shall communicate through defined interfaces rather than reaching directly into unrelated feature internals.

---

# 3. Presentation Layer

### AND-004 — UI State Driven by Application State
**Priority:** P0

User interfaces shall represent authoritative application/session state rather than independently inventing connectivity state.

### AND-005 — No Networking Logic in UI
**Priority:** P0

UI components shall not directly implement tunnel, relay, authentication, or low-level network operations.

### AND-006 — Lifecycle-Aware UI
**Priority:** P0

UI state shall tolerate activity recreation, configuration changes, backgrounding, and process recreation.

### AND-007 — Accessibility
**Priority:** P1

Core connectivity controls shall be usable with Android accessibility services and clear status semantics.

---

# 4. Domain/Application Layer

### AND-008 — Session State Machine
**Priority:** P0

The application layer shall expose a single coherent model of connection/session state.

### AND-009 — Provider/Receiver Roles
**Priority:** P0

Provider and Receiver behavior shall be represented as explicit roles rather than ambiguous client modes.

### AND-010 — Command-Based Operations
**Priority:** P0

Operations such as request, accept, reject, start, stop, and revoke shall be represented as controlled application actions.

### AND-011 — Failure Translation
**Priority:** P0

Low-level failures shall be translated into meaningful domain/application errors before reaching the UI.

---

# 5. Control Plane Client

### AND-012 — Authenticated API Client
**Priority:** P0

Backend communication shall use authenticated, protected transport and centrally managed session credentials.

### AND-013 — API Boundary
**Priority:** P0

Feature modules shall communicate with backend functionality through typed service interfaces rather than raw HTTP calls scattered throughout the application.

### AND-014 — Signaling Client
**Priority:** P0

Signaling shall be isolated behind a signaling interface that can support the selected real-time transport without coupling UI code to protocol details.

### AND-015 — Retry Policy
**Priority:** P0

Control-plane retries shall be bounded, cancellation-aware, and compatible with idempotent backend operations.

---

# 6. VPN Integration

### AND-016 — Android VPNService Boundary
**Priority:** P0

The Android VPN layer shall be isolated behind a well-defined interface around Android's VPNService functionality.

### AND-017 — Explicit User Authorization
**Priority:** P0

The application shall obtain the Android VPN authorization required before establishing the VPN interface.

### AND-018 — Foreground Service Lifecycle
**Priority:** P0

Long-running VPN operation shall use the appropriate Android foreground-service model and user-visible notification requirements.

### AND-019 — Stop Semantics
**Priority:** P0

Stopping or revoking a session shall cause the VPN/tunnel data plane to stop forwarding traffic within defined bounded time.

### AND-020 — No Hidden VPN
**Priority:** P0

The application shall not maintain an undisclosed or unauthorized VPN session.

---

# 7. Tunnel Engine Boundary

### AND-021 — Isolated Tunnel Engine
**Priority:** P0

Packet processing and transport logic shall be isolated from presentation and account-management code.

### AND-022 — Transport Abstraction
**Priority:** P0

The tunnel engine shall communicate through an abstract transport interface so direct and relay paths can be selected without rewriting packet-processing logic.

### AND-023 — Session-Key Boundary
**Priority:** P0

Tunnel session keys and related cryptographic material shall be managed through a dedicated security boundary and shall not be exposed to UI components.

### AND-024 — Bounded Buffers
**Priority:** P0

Packet queues and buffers shall have explicit memory limits and backpressure behavior.

### AND-025 — Packet Isolation
**Priority:** P0

Packets from different sessions shall never be mixed by the application data plane.

---

# 8. Direct and Relay Transport

### AND-026 — Path Abstraction
**Priority:** P0

The application shall represent direct and relay transport as interchangeable path implementations behind a common interface.

### AND-027 — Path Selection
**Priority:** P0

The connection manager shall select an available authorized path according to negotiated connectivity and policy.

### AND-028 — Relay Fallback
**Priority:** P0

Failure of a direct path shall permit relay fallback when the session remains authorized and policy allows it.

### AND-029 — Path Migration Boundary
**Priority:** P1

The architecture should permit future path migration/reconnection without exposing transport implementation details to the UI.

---

# 9. Local Persistence

### AND-030 — Minimal Local Data
**Priority:** P0

The client shall persist only data needed for account operation, user preferences, secure session recovery where supported, and diagnostics.

### AND-031 — No Persistent Traffic Payloads
**Priority:** P0

The application shall not persist forwarded application traffic payloads.

### AND-032 — Secure Credential Storage
**Priority:** P0

Long-lived credentials or sensitive key material shall use Android-appropriate protected storage mechanisms.

### AND-033 — Local Data Encryption
**Priority:** P0

Sensitive locally persisted data shall receive appropriate protection against unauthorized device access.

---

# 10. Security Module

### AND-034 — Central Security Abstraction
**Priority:** P0

Cryptographic operations and sensitive credential handling shall be centralized behind tested interfaces.

### AND-035 — Key Lifecycle
**Priority:** P0

The client shall define creation, use, rotation, expiration, and destruction behavior for relevant keys.

### AND-036 — Secret Redaction
**Priority:** P0

Secrets shall not be written to logs, analytics, crash reports, or ordinary UI state.

### AND-037 — Device Revocation
**Priority:** P0

The client shall respond to authoritative device/session revocation state and terminate affected connectivity.

---

# 11. Connectivity Lifecycle

### AND-038 — Request Lifecycle
**Priority:** P0

A connection request shall progress through explicit states such as requested, authorized, negotiating, connected, degraded, revoked, failed, and terminated.

### AND-039 — Cancellation
**Priority:** P0

Users and the system shall be able to cancel pending connectivity operations safely.

### AND-040 — Timeout Handling
**Priority:** P0

Connection establishment shall have bounded timeouts and shall not remain indefinitely in an intermediate state.

### AND-041 — Reconnection Policy
**Priority:** P1

Temporary network failures should use controlled reconnection where authorization remains valid.

---

# 12. Android Lifecycle & Resource Management

### AND-042 — Process Death Recovery
**Priority:** P0

The application shall define safe behavior when Android terminates the application process.

### AND-043 — Network Change Handling
**Priority:** P0

The client shall detect relevant network changes and adapt or terminate according to tunnel capabilities and authorization.

### AND-044 — Battery Awareness
**Priority:** P0

The architecture shall minimize unnecessary background work and account for Android battery-management constraints.

### AND-045 — Memory Protection
**Priority:** P0

Packet processing, buffers, caches, and UI state shall use bounded resource consumption.

### AND-046 — Thermal/Resource Pressure
**Priority:** P1

The application should degrade gracefully under severe device resource pressure rather than continuously consuming uncontrolled resources.

---

# 13. Notifications & User Visibility

### AND-047 — Active Sharing Visibility
**Priority:** P0

When the device is actively providing connectivity, the user shall receive appropriate status visibility.

### AND-048 — Active Receiving Visibility
**Priority:** P0

When Linko is actively forwarding traffic for the Receiver, the user shall have appropriate connection status visibility.

### AND-049 — Stop Control
**Priority:** P0

Where Android permits, users shall have an accessible mechanism to stop active connectivity.

---

# 14. Diagnostics

### AND-050 — Structured Diagnostics
**Priority:** P1

The client shall expose structured diagnostic state for connection, authentication, signaling, transport, and VPN failures.

### AND-051 — Privacy-Safe Diagnostics
**Priority:** P0

Diagnostics shall avoid exposing traffic payloads, credentials, private keys, and unnecessary personal data.

### AND-052 — User-Friendly Errors
**Priority:** P1

Technical failures shall be translated into actionable user-facing explanations where possible.

---

# 15. Testing Architecture

### AND-053 — Unit-Testable Domain
**Priority:** P0

Core session and business logic shall be testable independently of Android UI components.

### AND-054 — Mockable Transport
**Priority:** P0

Network and relay transports shall be replaceable with deterministic test implementations.

### AND-055 — VPN Integration Tests
**Priority:** P0

The VPN boundary shall have dedicated integration testing on supported Android versions/devices.

### AND-056 — Failure Injection
**Priority:** P1

The architecture should support controlled simulation of network loss, relay failure, authorization revocation, and process termination.

---

# 16. Module Dependency Rules

### AND-057 — Dependency Direction
**Priority:** P0

High-level application/domain modules shall not depend directly on platform-specific low-level implementations when an abstraction can isolate them.

### AND-058 — No Feature Cycles
**Priority:** P0

Feature modules shall not form circular dependencies.

### AND-059 — Tunnel Isolation
**Priority:** P0

The tunnel engine shall not depend on UI modules.

### AND-060 — Security Isolation
**Priority:** P0

Security/key-management code shall not depend on presentation code.

---

# 17. Build & Release

### AND-061 — Reproducible Builds
**Priority:** P1

Release builds should be reproducible from versioned source and controlled dependencies.

### AND-062 — Dependency Control
**Priority:** P0

Third-party Android dependencies shall be inventoried and reviewed for security and maintenance risk.

### AND-063 — Release Signing Protection
**Priority:** P0

Production signing credentials shall be protected outside the source repository.

### AND-064 — Minimum Supported Android Version
**Priority:** P0

The project shall explicitly define its minimum and target Android API levels before implementation is finalized.

---

# 18. Definition of Done

Phase 4.1 is complete when:

- [x] Android module boundaries are defined
- [x] Presentation/application/domain boundaries are defined
- [x] VPNService integration boundary is defined
- [x] Tunnel engine boundary is defined
- [x] Direct/relay transport abstraction is defined
- [x] Local storage boundaries are defined
- [x] Security boundaries are defined
- [x] Lifecycle/resource requirements are defined
- [x] Diagnostics requirements are defined
- [x] Testing architecture is defined
- [x] Dependency rules are defined
- [x] Build/release requirements are defined

## Next architecture task

**4.2 — Android Service & VPN Lifecycle Design**
