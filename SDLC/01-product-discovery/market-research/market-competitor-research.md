# Phase 1.9 — Market & Competitor Research

## Status

**REVIEW — READY FOR PROJECT-OWNER APPROVAL**

## Research date

**2026-08-18**

## Purpose

Assess the existing market, adjacent technologies, competitive alternatives, technical precedent, strategic gaps, and launch constraints relevant to Linko.

This research does not assume that every technically related product is a direct competitor. Linko occupies a proposed category: **trusted remote connectivity sharing**.

---

# 1. Executive Finding

The market already contains products that prove important pieces of Linko's technical concept, but there is a meaningful product-positioning gap between:

- conventional physical hotspot/tethering,
- remote-access/network-overlay products, and
- consumer applications specifically designed around **a trusted person requesting and receiving temporary Internet connectivity from another person's device at a distance**.

The strongest technical precedent found is the **exit-node model** used by products such as Tailscale: an authorized device can route Internet traffic through another device. Tailscale documents Android exit-node support, but also notes that Android exit nodes use userspace routing and are not highly performant, demonstrating both feasibility and the importance of Android performance constraints. citeturn1search0turn1search2

Android's official `VpnService` API provides the underlying mechanism for creating a virtual network interface and exchanging packets through a tunnel. citeturn0search1turn0search8

Therefore, the core Linko hypothesis is **technically plausible but not yet proven as a consumer product at the required reliability, simplicity, carrier compatibility, and economics level**.

---

# 2. Competitive Landscape

## Category A — Traditional hotspot/tethering

Examples include Android's built-in hotspot and carrier tethering.

### Strengths

- Familiar.
- Simple.
- Usually low infrastructure cost.
- Direct device-to-device local connectivity.

### Weakness

Physical proximity is normally required.

### Linko opportunity

Move the sharing relationship from local radio range to Internet-based connectivity between authorized users.

---

# 3. Category B — Tethering Applications

## PdaNet+

PdaNet+ is a long-established Android connectivity-sharing product. Its Google Play listing reports 10M+ downloads and describes Wi-Fi Direct and Bluetooth modes, while explicitly warning that Android/no-root technical limitations mean the solution is not universal across devices. citeturn1search1turn1search3

### What it proves

- There is established demand for alternative Android connectivity-sharing mechanisms.
- Android networking restrictions create real implementation challenges.
- Users value tools that make Internet sharing easier.

### What Linko can do differently

PdaNet's core model is primarily tethering/local-device sharing. Linko's differentiator is the **remote trusted Provider → Receiver relationship** rather than merely replacing the phone's local hotspot mechanism.

### Lesson for Linko

Never claim universal device compatibility before testing. PdaNet's own documentation demonstrates that Android connectivity-sharing capabilities vary by device and configuration. citeturn1search1

---

# 4. Category C — Mesh / Private Networking Platforms

## Tailscale

Tailscale provides an important technical precedent. Its exit-node feature routes Internet traffic through a selected device on a private network, and its Android client can use an Android device as an exit node. citeturn1search0turn1search2

### Strengths

- Mature identity-based networking model.
- Explicit authorization.
- Secure networking architecture.
- Exit-node capability.
- Android support.
- Strong networking tooling.

### Weakness relative to Linko's proposed consumer experience

The product is fundamentally a networking platform rather than a consumer "ask my trusted friend for Internet" experience.

### Critical technical lesson

Tailscale states that Android exit nodes use userspace routing and may be too slow for many cases, and recommends power for extended use. citeturn1search2

This is a major Linko risk: **Provider-side forwarding on Android can consume CPU, battery, and bandwidth.**

### Strategic conclusion

Linko should study Tailscale's identity, authorization, tunnel, routing, and failure-handling approaches while differentiating its consumer workflow.

---

# 5. Category D — VPN / Remote Networking

VPN products can route a device's traffic through remote infrastructure or an authorized gateway.

### Strengths

- Mature tunnel concepts.
- Strong security ecosystem.
- Established Android support.
- Existing user understanding of VPNs.

### Weakness for Linko

A conventional VPN normally connects a user to a VPN server/service. Linko's central product relationship is **user-to-user connectivity sharing**, with the Provider's existing Internet connection serving as the resource.

### Product distinction

Linko should not be marketed merely as another VPN. Its user story is:

> **Someone you trust can temporarily provide your Internet connection to you from somewhere else.**

---

# 6. Category E — Remote Access / Network Tools

Remote-access networking tools demonstrate that Internet-based routing between geographically separated devices is a valid technical category.

Google Play's current VpnService policy explicitly recognizes certain network-related tools, including remote access, as a permitted use category. citeturn0search0turn0search2

### Linko implication

If Linko uses Android `VpnService`, Google Play compliance must be treated as a first-class product requirement rather than a launch-time checklist.

---

# 7. Android Technical Market Constraint

Android's official `VpnService` API creates a virtual network interface and gives the application a file descriptor through which packets can be processed and exchanged with a remote tunnel. citeturn0search8turn0search18

The general flow is:

```text
Android device
     ↓
VpnService
     ↓
Virtual network interface
     ↓
Tunnel processing
     ↓
Remote endpoint
     ↓
Internet
```

This supports the technical direction already established for Linko, but does not by itself solve Provider-side Internet forwarding, NAT traversal, carrier restrictions, battery efficiency, or relay economics.

---

# 8. Google Play Constraint

Google Play currently restricts use of `VpnService` to apps whose core functionality is VPN or specified permitted categories such as remote-access/network tools, carrier connectivity services, device security, parental control, and related cases. Apps using it must document the use in the Play listing and encrypt data from the device to the VPN tunnel endpoint. citeturn0search0turn0search2

Google Play also prohibits manipulating traffic from other apps for monetization purposes. citeturn0search0turn0search3

### Linko requirement

Before production launch, Linko must receive a dedicated policy/compliance assessment confirming that its exact `VpnService` implementation and product behavior fit an allowed category.

**This is a launch-critical risk, not a minor implementation detail.**

---

# 9. Competitive Comparison

| Capability | Traditional Hotspot | PdaNet+ | Tailscale Exit Node | Conventional VPN | Linko Target |
|---|---|---|---|---|---|
| Remote over Internet | Usually no | Primarily local/device tethering | Yes | Yes | **Yes** |
| Provider is another user | Yes, locally | Usually local sharing | Authorized network device | Usually provider is service | **Yes** |
| Trusted-person workflow | Low | Low | Strong technical identity | Varies | **Core** |
| Explicit Provider approval per session | Limited | Limited | Policy-based | Service-based | **Core** |
| Consumer "request Internet" experience | No | No | No | No | **Core** |
| Provider can stop session | Yes | Yes | Yes | Service controls | **Core** |
| Android routing | Native/system | Yes | Yes | Yes | **Required** |
| Relay fallback | Usually no | No/limited | Infrastructure dependent | Yes | **Required where feasible** |
| Designed around remote human assistance | No | No | No | No | **Yes** |

The table is a product-positioning comparison, not a claim that every feature is implemented identically across products.

---

# 10. Linko's Potential Competitive Advantage

The strongest defensible advantage is unlikely to be the raw VPN/tunnel technology alone because networking technologies are mature and competitors already route traffic through remote devices.

The advantage should instead be the **complete trusted-connectivity experience**:

```text
Identity
   ↓
Trust relationship
   ↓
One-tap request
   ↓
Explicit Provider consent
   ↓
Automatic connection negotiation
   ↓
Controlled session
   ↓
Usage transparency
   ↓
Easy termination
```

Potential moat areas:

1. User trust graph.
2. Simple connectivity-sharing UX.
3. Reliability across difficult mobile networks.
4. Efficient Android Provider forwarding.
5. Cost-efficient relay infrastructure.
6. Abuse-resistant session controls.
7. Strong regional carrier/network compatibility knowledge.
8. Provider incentives and retention.
9. Institutional and telecom partnerships.

---

# 11. Market Opportunity Hypothesis

The initial market should not be "everyone with a phone."

Start with situations where the problem is obvious:

### Priority 1 — Students

Connectivity emergencies are easy to understand and easy to test through campus communities.

### Priority 2 — Friends and family

Trusted relationships naturally match Linko's authorization model.

### Priority 3 — Travelers

Distance and temporary connectivity needs are particularly relevant.

### Priority 4 — Institutions

Universities and organizations may eventually provide structured connectivity assistance.

### Priority 5 — Global consumers

Only after technical reliability, compliance, economics, and abuse controls are proven.

---

# 12. Customer Discovery Questions

Research should test behavior, not merely ask whether users "like the idea."

Ask:

1. What do you do when your mobile data finishes unexpectedly?
2. Have you ever asked a friend/family member for Internet help?
3. How far away was that person?
4. Would you allow a trusted person to use some of your data remotely?
5. What would make you uncomfortable?
6. Would you set a data or time limit?
7. Would you pay for enhanced reliability?
8. Would you accept a reward for providing connectivity?
9. How much control would you expect?
10. What would make you stop using Linko?

---

# 13. Competitor Research Conclusions

### Finding 1

Remote traffic routing through another device is technically established by products such as Tailscale's exit nodes. citeturn1search0turn1search2

### Finding 2

Android can expose a virtual network interface through `VpnService`, providing a legitimate technical foundation for tunnel-based routing. citeturn0search8turn0search18

### Finding 3

Android connectivity-sharing products demonstrate real user demand but also demonstrate device-specific limitations. citeturn1search1

### Finding 4

Battery/performance on Android Provider devices is a significant technical constraint. Tailscale specifically warns about Android exit-node performance and power consumption. citeturn1search2turn0search4

### Finding 5

Google Play policy is a significant constraint for any Linko design relying on `VpnService`. citeturn0search0turn0search2

### Finding 6

Linko's strongest differentiation should be the **trusted human connectivity-sharing workflow**, not simply claiming to have a unique tunnel protocol.

---

# 14. Strategic Recommendation

Proceed with Linko, but position the product around **trusted remote connectivity sharing** rather than "a VPN that shares data."

Technical research should benchmark:

- Android `VpnService` performance.
- Provider battery drain.
- Direct connectivity success rate.
- Relay success rate.
- Mobile-carrier compatibility.
- Connection latency.
- Bandwidth throughput.
- Session reliability.
- Google Play policy compatibility.

Business research should validate:

- Provider willingness.
- Receiver willingness.
- Pricing tolerance.
- Provider incentives.
- Infrastructure cost per GB/session.
- Retention.

---

# 15. Research Gaps

The following cannot be answered reliably by desk research alone:

- Exact Ghana carrier compatibility.
- Exact performance on target Android devices.
- Real-world direct-path success rates.
- Real-world relay costs.
- User willingness to share mobile data.
- User willingness to pay.
- Provider battery impact under sustained forwarding.
- Regulatory interpretation for the final architecture in each launch market.

These must become experiments in later phases.

---

# 16. Phase 1.9 Acceptance Criteria

- [x] Market categories identified
- [x] Direct and adjacent competitors identified
- [x] Tethering alternatives researched
- [x] Remote-networking precedent researched
- [x] Android technical constraints researched
- [x] Google Play policy constraint identified
- [x] Competitive differentiation defined
- [x] Target market priority defined
- [x] Customer discovery questions defined
- [x] Market opportunity hypothesis defined
- [x] Research gaps documented
- [x] Strategic recommendation defined

---

# Review Gate

**Status:** READY FOR PROJECT-OWNER REVIEW AND APPROVAL

This deliverable is not marked complete until the project owner explicitly approves it.

## Next step after approval

**Phase 1.10 — Technical Feasibility**

Phase 1 remains **IN PROGRESS**.

---

# Sources

- Android `VpnService` API documentation: urlAndroid Developers — VpnServicehttps://developer.android.com/reference/android/net/VpnService
- Android `VpnService.Builder`: urlAndroid Developers — VpnService.Builderhttps://developer.android.com/reference/android/net/VpnService.Builder
- Google Play VpnService policy: urlGoogle Play — VpnService policyhttps://support.google.com/googleplay/android-developer/answer/12564964
- Tailscale exit nodes: urlTailscale — Exit nodeshttps://tailscale.com/docs/features/exit-nodes
- Tailscale Android exit-node setup: urlTailscale — Use exit nodes on Androidhttps://tailscale.com/docs/features/exit-nodes/how-to/setup?tab=android
- PdaNet+ Google Play listing: urlPdaNet+ on Google Playhttps://play.google.com/store/apps/details?id=com.pdanet
