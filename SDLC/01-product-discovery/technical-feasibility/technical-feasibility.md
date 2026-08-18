# Phase 1.10 — Technical Feasibility

## Status

**REVIEW — READY FOR PROJECT-OWNER APPROVAL**

## Purpose

Determine whether the Linko core concept is technically achievable on Android and the public Internet, identify the architecture constraints, and define the experiments required before committing to full production implementation.

---

# 1. Feasibility Question

> Can Linko securely route a Receiver's Internet traffic through an authorized Provider's available Internet connection over the Internet, using Android-supported networking mechanisms, while maintaining acceptable reliability, battery usage, latency, security, and operating cost?

**Conclusion:** The core networking concept is **technically plausible**, but universal operation is **not yet proven**. The project must validate the exact Provider-to-Receiver traffic path on real Android devices and real carrier/Wi-Fi networks before claiming broad compatibility.

Android's `VpnService` provides a virtual IP interface: applications can read outgoing IP packets from the interface and inject incoming packets after processing them through a tunnel. Android's documented flow includes user preparation/consent, creating the VPN interface, creating a remote tunnel, exchanging packets, and gracefully shutting down on revocation. citeturn0search0

---

# 2. Feasibility Verdict

| Area | Assessment | Confidence | Required action |
|---|---|---:|---|
| Android virtual networking | FEASIBLE | High | Prototype with `VpnService` |
| Device-level traffic capture | FEASIBLE | High | Validate selected routes/apps |
| Secure tunnel | FEASIBLE | High | Select and implement proven protocol |
| Remote Provider-to-Receiver routing | PLAUSIBLE | Medium | End-to-end real-device test |
| Direct P2P connectivity | PLAUSIBLE | Medium | NAT traversal experiments |
| Relay fallback | FEASIBLE | High | Prototype controlled relay |
| Provider traffic forwarding | PLAUSIBLE | Medium | Validate Android routing/forwarding model |
| Background operation | FEASIBLE WITH CONSTRAINTS | Medium | Test foreground-service behavior |
| Universal carrier compatibility | NOT PROVEN | Low | Carrier/network test matrix |
| Google Play distribution | FEASIBLE WITH POLICY REQUIREMENTS | Medium | Complete policy/declaration review |
| Global scalability | FEASIBLE IN PRINCIPLE | Medium | Cost and regional architecture study |
| Sustainable unit economics | UNKNOWN | Low | Measure bandwidth/relay cost |

---

# 3. Android Networking Foundation

Android officially exposes `VpnService` for applications that need to build VPN solutions. It creates a virtual network interface and provides a file descriptor through which packets routed to the VPN can be processed and exchanged with a remote tunnel. citeturn0search0

This gives Linko a credible foundation for the Receiver-side traffic-routing component.

### Basic model

```text
Receiver Android
      │
      │ Android VpnService
      ▼
Virtual IP interface
      │
      │ encrypted tunnel
      ▼
Linko network path
      │
      ▼
Provider-side forwarding component
      │
      ▼
Provider Internet
```

The Provider-side component still requires careful validation; `VpnService` alone does not automatically turn an Android phone into a general-purpose remote Internet gateway.

---

# 4. Required Technical Architecture

The likely architecture is divided into five major components:

```text
┌───────────────────┐
│ Receiver Android  │
│                   │
│ VpnService        │
│ Tunnel Client     │
└─────────┬─────────┘
          │
          │ encrypted transport
          ▼
┌───────────────────┐
│ Linko Signaling   │
│ / Control Plane   │
└─────────┬─────────┘
          │
          │ connection negotiation
          ▼
┌───────────────────┐
│ Direct Path OR    │
│ Relay             │
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│ Provider Android  │
│                   │
│ Forwarding/Tunnel │
│ Component         │
└─────────┬─────────┘
          │
          ▼
    Provider Internet
```

The control plane and data plane must remain separate.

---

# 5. Control Plane vs Data Plane

## Control plane

Responsible for:

- Authentication
- Trusted relationships
- Connectivity requests
- Provider approval
- Session authorization
- Device registration
- Signaling
- Connection state
- Policy/configuration
- Session termination

## Data plane

Responsible for:

- Actual user traffic
- Packet forwarding
- Encryption
- Tunnel transport
- Direct connectivity
- Relay fallback

The backend should not unnecessarily inspect or persist user traffic.

---

# 6. Connection Establishment

The preferred sequence is:

```text
Receiver
  │
  │ request
  ▼
Backend
  │
  │ notify
  ▼
Provider
  │
  │ approve
  ▼
Signaling
  │
  ├──── candidate exchange ────┐
  │                            │
  ▼                            ▼
Direct path                 Relay path
  │                            │
  └──────────┬─────────────────┘
             ▼
       Secure session
```

Linko should prefer a direct path where safe and technically available, with an authenticated relay fallback when necessary.

---

# 7. NAT and Firewall Feasibility

The public Internet frequently places mobile devices behind NAT, carrier-grade NAT, firewalls, or restrictive network policies.

Therefore:

> **Direct device-to-device connectivity cannot be assumed.**

The MVP must include a NAT traversal strategy and a relay fallback.

The exact protocol and infrastructure should be selected during Phase 3 and Phase 6 rather than hard-coded into this feasibility document.

---

# 8. Provider-Side Feasibility

This is a critical unknown.

The Provider must be able to receive authorized traffic from the tunnel and forward it through the Provider's Internet connection without exposing the Provider's device or local network unnecessarily.

The engineering team must validate:

- Packet forwarding behavior.
- Routing.
- DNS handling.
- IPv4.
- IPv6.
- MTU.
- TCP/UDP behavior.
- Network switching.
- Mobile-data behavior.
- Battery impact.
- Carrier restrictions.
- Android OS restrictions.

**Do not declare this solved until a working two-device prototype proves it.**

---

# 9. Android Background Execution

Android places restrictions on background services. The Android documentation states that VPN applications on Android 8.0+ need to promote the VPN service to the foreground after launch or the system can shut the app down. citeturn0search0turn0search5

Therefore the Linko session architecture must account for:

- Foreground service requirements.
- Persistent user-visible notification.
- Battery optimization behavior.
- Process death.
- Service restart.
- Network changes.
- VPN revocation.
- Device reboot behavior.

Always-on VPN is an Android capability, but it is not the same as Linko's temporary sharing-session requirement and should not be assumed to solve session persistence automatically. citeturn0search0turn0search6

---

# 10. Google Play Feasibility

Google Play currently restricts use of `VpnService`. Apps using it must have VPN functionality as core functionality or fall within a listed permitted category/exception. Google also requires documentation of VPN usage in the Play listing and encryption from the device to the VPN tunnel endpoint. citeturn0search2turn0search3

Linko's product definition therefore must be aligned with the applicable VPN/network-tool policy before production release.

The project must not design monetization around manipulating traffic from other apps. Google Play explicitly prohibits using `VpnService` to redirect or manipulate other-app traffic for monetization purposes. citeturn0search2turn0search4

If Linko's implementation or data use changes, the relevant Play declaration and disclosures must be kept accurate. citeturn0search3

---

# 11. Security Feasibility

A secure implementation is feasible, but security must be designed into the architecture.

Minimum security model:

```text
Authenticated identity
        ↓
Trusted relationship
        ↓
Explicit Provider consent
        ↓
Short-lived session authorization
        ↓
Authenticated tunnel
        ↓
Encrypted traffic
        ↓
Session termination / revocation
```

The tunnel must not rely on trust merely because two users are contacts.

---

# 12. Threats to Validate

Major technical threats:

- Account takeover.
- Forged session authorization.
- Unauthorized Provider access.
- Unauthorized Receiver access.
- Session hijacking.
- Replay attacks.
- Relay abuse.
- Traffic amplification.
- Denial of service.
- Malicious Provider behavior.
- Malicious Receiver behavior.
- Device compromise.
- Credential theft.

These become detailed security requirements in Phase 10.

---

# 13. Reliability Feasibility

A successful connection must survive realistic conditions.

Test:

- Temporary packet loss.
- High latency.
- Network switching.
- Wi-Fi → mobile data.
- Mobile data → Wi-Fi.
- Provider screen off.
- Receiver screen off.
- Temporary Internet loss.
- Backend signaling interruption.
- Relay interruption.
- App process termination.
- VPN permission revocation.

The session state machine must distinguish:

```text
REQUESTED
APPROVED
CONNECTING
CONNECTED
DEGRADED
DISCONNECTING
DISCONNECTED
FAILED
EXPIRED
```

---

# 14. Performance Feasibility

The biggest performance risks are:

- Encryption overhead.
- Packet-copy overhead.
- Android CPU usage.
- Battery drain.
- Network latency.
- Relay latency.
- Bandwidth overhead.
- MTU fragmentation.

The prototype must measure:

- Connection establishment time.
- Round-trip latency.
- Throughput.
- Packet loss.
- CPU usage.
- Memory usage.
- Battery consumption.
- Relay overhead.

No performance target is considered validated until measured on physical devices.

---

# 15. Economic Feasibility

Relay bandwidth may become one of Linko's largest variable infrastructure costs.

The economic model must therefore measure:

```text
Traffic generated
        ↓
Direct vs relay ratio
        ↓
Bandwidth consumed
        ↓
Infrastructure cost
        ↓
Cost per session
        ↓
Cost per active user
```

The MVP should optimize for direct paths where technically appropriate, while retaining relay capability for compatibility.

Economic feasibility remains **UNPROVEN** until real traffic measurements exist.

---

# 16. Device Compatibility

Initial support should be deliberately narrow.

### MVP target

- Android smartphones.
- A defined minimum Android API level.
- Devices with supported VPN functionality.
- Devices that can sustain foreground VPN operation.

The minimum API level must be selected after Phase 4 architecture and device testing.

Do not promise every Android phone at launch.

---

# 17. Network Compatibility Matrix

The engineering team must create a real test matrix:

| Provider | Receiver | Expected test |
|---|---|---|
| Mobile A | Mobile A | Same-carrier baseline |
| Mobile A | Mobile B | Cross-carrier |
| Mobile B | Wi-Fi | Mixed network |
| Wi-Fi | Mobile A | Mixed network |
| Wi-Fi | Wi-Fi | Internet baseline |
| Carrier NAT | Carrier NAT | Difficult NAT |
| Restricted network | Open network | Restrictive environment |

Results must be stored as test evidence, not assumptions.

---

# 18. Prototype Strategy

Build feasibility in progressively harder stages.

## Prototype 1 — Local tunnel

Prove that an Android `VpnService` can capture and route selected traffic through a controlled tunnel.

## Prototype 2 — Remote server

Prove encrypted packet exchange between Android and a remote endpoint.

## Prototype 3 — Provider forwarding

Prove that the Provider device can safely forward authorized traffic through its Internet connection.

## Prototype 4 — Android-to-Android

Replace the simple endpoint with another Android device.

## Prototype 5 — NAT traversal

Test direct connectivity under realistic NAT conditions.

## Prototype 6 — Relay fallback

Add an authenticated relay when direct connectivity fails.

## Prototype 7 — Real-world pilot

Test multiple carriers, Wi-Fi networks, cities, devices, and operating conditions.

---

# 19. Feasibility Decision Gates

### Gate A — Android packet routing

**Pass:** VPN interface captures and processes expected traffic.

### Gate B — Secure remote tunnel

**Pass:** Encrypted tunnel carries traffic reliably.

### Gate C — Provider forwarding

**Pass:** Authorized Receiver traffic reaches the Internet through Provider connectivity.

### Gate D — Remote Android-to-Android

**Pass:** Two real Android devices complete the core session.

### Gate E — Network diversity

**Pass:** Multiple independent network combinations work.

### Gate F — Reliability

**Pass:** Connection remains usable under defined interruptions.

### Gate G — Economics

**Pass:** Measured infrastructure cost supports a credible business model.

### Gate H — Distribution/compliance

**Pass:** Architecture and product behavior can comply with applicable Android/Google Play requirements.

---

# 20. What Is Proven vs Unproven

## Proven by platform capability

- Android provides `VpnService` for VPN applications and virtual IP interfaces. citeturn0search0
- Android provides mechanisms for VPN services to process and exchange packets through a tunnel. citeturn0search0
- Android imposes foreground-service/background execution considerations for VPN apps. citeturn0search0turn0search5
- Google Play has a defined policy framework for `VpnService` use. citeturn0search2turn0search3

## Not yet proven for Linko

- Universal Provider-side forwarding on Android.
- Universal carrier compatibility.
- Universal NAT traversal.
- Acceptable battery usage.
- Acceptable relay costs.
- Production-scale reliability.
- Market willingness to share bandwidth.
- Global regulatory compatibility.

---

# 21. Technical Architecture Recommendation

At this stage, the recommended direction is:

```text
Android Client
     │
     ├── Authentication
     ├── Trusted contacts
     ├── Session controller
     └── VpnService / tunnel engine
              │
              ▼
       Secure data channel
              │
        ┌─────┴─────┐
        ▼           ▼
   Direct path    Relay
        │           │
        └─────┬─────┘
              ▼
      Provider-side tunnel
              │
              ▼
      Provider Internet
```

The exact tunnel protocol, NAT traversal technology, relay stack, backend stack, and Android package architecture are **Phase 3–9 decisions** and must be documented there.

---

# 22. Feasibility Risks

| Risk | Severity | Mitigation |
|---|---|---|
| Android restrictions | High | Prototype early on physical devices |
| Carrier NAT | High | NAT traversal + relay |
| Provider forwarding limitations | Critical | Dedicated feasibility prototype |
| Battery drain | High | Measure and optimize |
| Relay cost | High | Direct-path preference + economics |
| Play policy | High | Policy review before release |
| Network instability | High | Session state/reconnect logic |
| Security compromise | Critical | Threat model + secure tunnel |
| Device fragmentation | Medium | Narrow MVP device matrix |
| Regulatory constraints | High | Market-by-market legal review |

---

# 23. Phase 1.10 Final Assessment

### Overall technical feasibility

**CONDITIONALLY FEASIBLE.**

The Android platform supplies a credible technical foundation for the Receiver-side tunnel through `VpnService`, and secure remote tunneling is a standard networking pattern. citeturn0search0

However, Linko's unique requirement is not simply creating a VPN. The difficult part is making **one Android user's Internet connection act as the authorized upstream path for another Android user over distance** while handling NAT, carrier restrictions, Android lifecycle constraints, security, battery, reliability, and cost.

Therefore the correct engineering decision is:

> **Proceed to architecture and prototyping, but treat Provider-side forwarding, cross-network compatibility, economics, and production reliability as explicit validation gates.**

---

# 24. Phase 1.10 Acceptance Criteria

- [x] Core technical feasibility question defined.
- [x] Android networking foundation researched.
- [x] Control/data plane defined.
- [x] Connection architecture defined at feasibility level.
- [x] NAT/firewall constraints documented.
- [x] Provider-side unknowns identified.
- [x] Android lifecycle constraints documented.
- [x] Google Play constraints researched.
- [x] Security feasibility defined.
- [x] Reliability risks defined.
- [x] Performance risks defined.
- [x] Economic feasibility requirements defined.
- [x] Device/network testing matrix defined.
- [x] Prototype sequence defined.
- [x] Decision gates defined.
- [x] Proven vs unproven claims separated.
- [x] Final feasibility assessment documented.

---

# Review Gate

**Status:** READY FOR PROJECT-OWNER REVIEW AND APPROVAL

This document does not claim that Linko is already technically proven. It records a conditional feasibility conclusion and the experiments required to prove the remaining critical assumptions.

## Next step after approval

**Phase 1.11 — Business Model Hypotheses**

Phase 1 remains **IN PROGRESS**.
