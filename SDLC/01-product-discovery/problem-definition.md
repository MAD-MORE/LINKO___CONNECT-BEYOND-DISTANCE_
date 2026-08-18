# Phase 1.1 — Problem Definition

## Status

**CURRENT — IN PROGRESS**

This document is the first execution deliverable of Phase 1. It must be reviewed and accepted before Phase 1.2 begins.

## 1. Problem statement

People can share Internet with someone nearby using conventional Wi-Fi hotspots, USB tethering, or Bluetooth-based methods. These approaches depend on physical proximity and therefore do not solve the problem when the person who needs connectivity and the person willing to share connectivity are far apart.

Linko addresses this gap by creating an Internet-based connection path between two authorized users. A Provider can voluntarily make an existing Internet connection available to a Receiver through a secure tunnel, subject to the Provider's network, device, carrier, bandwidth, battery, NAT/firewall, operating-system, and policy limitations.

## 2. Who experiences the problem?

Primary users:

- Students who run out of mobile data while a trusted friend still has connectivity.
- Friends or family members who want to help someone remotely.
- Travelers who need temporary connectivity and have a trusted contact with Internet access.
- Users who maintain multiple devices/SIMs and want controlled remote connectivity sharing.
- Users with suitable high-capacity or unlimited plans who voluntarily want to share connectivity.

## 3. Existing alternatives

Users currently rely on:

- Physical Wi-Fi hotspot sharing
- USB tethering
- Bluetooth tethering
- Buying additional data
- Sending money to another person to buy data
- Public Wi-Fi
- VPNs and remote networking tools for different use cases

These alternatives either require physical proximity, require the Receiver to obtain their own data, expose users to untrusted networks, or are not designed around simple person-to-person Internet sharing.

## 4. The gap

The key product gap is:

> There is no simple consumer experience centered on allowing a trusted person to voluntarily share their existing Internet connection with another trusted person over distance, with explicit approval and Provider-controlled limits.

## 5. Linko's proposed solution

Linko introduces a Provider/Receiver model:

```text
Provider has Internet
        ↓
Provider approves request
        ↓
Linko establishes secure session
        ↓
Receiver routes authorized traffic through session
        ↓
Provider can monitor and terminate session
```

Where direct peer connectivity is not possible, Linko may use controlled relay infrastructure. Relay use must be designed around security, performance, cost, and privacy requirements.

## 6. Important technical reality

Linko does **not** bypass the physical or commercial limits of Internet networks.

Distance itself is not the primary bottleneck because the connection can traverse the public Internet. However, Linko remains constrained by:

- Provider upload bandwidth
- Receiver download bandwidth
- Latency
- Packet loss
- Carrier restrictions
- NAT/firewall behavior
- Android background/network restrictions
- Battery consumption
- Relay availability
- Relay cost
- Provider data allowance
- ISP/carrier terms

These constraints are product requirements, not problems that should be hidden from users.

## 7. Why the problem is worth solving

Connectivity is increasingly necessary for education, communication, payments, work, navigation, and access to online services. Temporary loss of connectivity can therefore create an immediate practical problem.

A trusted person may already have sufficient connectivity to help. Linko's opportunity is to make that help technically possible over distance while preserving consent, control, transparency, and security.

## 8. Product hypothesis

**H1:** Users will value a simple way to request temporary Internet connectivity from trusted contacts when their own connectivity is unavailable or insufficient.

**H2:** Providers will share connectivity when they have clear control over who connects, how long the session lasts, and how much data is consumed.

**H3:** A technically reliable and secure remote-sharing experience can create enough user value to support sustainable premium services without requiring users to pay simply to maintain basic trust relationships.

## 9. Problem severity

| Problem | Severity | MVP relevance |
|---|---:|---:|
| Receiver has no/insufficient Internet | High | Core |
| Provider and Receiver are physically separated | High | Core |
| Provider lacks control over sharing | High | Core |
| Untrusted person gaining access | Critical | Core security |
| Excessive Provider data consumption | High | Core |
| Poor performance/latency | High | Core technical |
| Relay infrastructure cost | High | Core business/technical |
| Privacy concerns | Critical | Core security/privacy |

## 10. Problem boundaries

Linko will not claim to:

- Generate free mobile data.
- Remove carrier billing.
- Guarantee unlimited speed.
- Guarantee connectivity on every carrier/device.
- Eliminate Internet latency.
- Bypass carrier restrictions or terms of service.
- Give a Receiver access without Provider authorization.

## 11. Phase 1 acceptance criteria for this deliverable

- [x] Problem is explicitly defined.
- [x] Target users are identified.
- [x] Existing alternatives are identified.
- [x] Product gap is stated.
- [x] Proposed solution is stated.
- [x] Technical limitations are explicitly acknowledged.
- [x] Initial product hypotheses are documented.
- [x] Problem boundaries are documented.

## 12. Next deliverable

**Phase 1.2 — Product Vision**

Do not proceed to Phase 2. This remains inside Phase 1.
