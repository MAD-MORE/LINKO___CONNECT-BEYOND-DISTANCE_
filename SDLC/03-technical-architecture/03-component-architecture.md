# Phase 3.3 — Component Architecture

## Status
COMPLETE

## Component responsibilities

### Android Presentation
Owns onboarding, provider/receiver flows, session status, consent, settings and errors. It must never contain server authority.

### Android Session Manager
Owns local session lifecycle, reconnect policy, VPN lifecycle and synchronization with backend state.

### Android Tunnel Engine
Owns packet capture/forwarding through Android's supported VPN framework and transport integration. It does not decide user authorization.

### Identity Service
Authenticates users/devices and issues credentials/tokens according to the security architecture.

### Relationship Service
Creates and removes Provider/Receiver relationships and enforces visibility rules.

### Session Service
Creates, authorizes, updates, expires and terminates connectivity sessions.

### Signaling Service
Exchanges authenticated connection candidates and negotiation messages.

### Relay Service
Forwards protected traffic between authorized endpoints and enforces session/resource limits.

### Data Service
Provides durable storage for authoritative records.

### Policy Service
Evaluates quotas, restrictions, device/session policies and abuse decisions.

### Notification/Worker Layer
Processes non-critical asynchronous work without blocking session-critical paths.

### Observability Layer
Collects operational telemetry while enforcing sensitive-data exclusions.

## Dependency rule
Components communicate through explicit contracts. Direct database access from unrelated services is prohibited unless explicitly documented as an architectural exception.

## Acceptance
Every component has one primary responsibility, a defined trust boundary, API contract, failure behavior and data-access scope.
