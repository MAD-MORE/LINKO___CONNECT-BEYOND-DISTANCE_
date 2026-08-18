# Phase 1.4 — User Personas

## Status

**REVIEW — READY FOR PROJECT-OWNER APPROVAL**

## Persona methodology

These personas are initial working hypotheses derived from the target-user definition. They are not claims that these exact individuals exist. They must be validated through interviews, surveys, prototype testing, and beta evidence before being treated as research-validated personas.

## Persona 1 — Kwame, the Connectivity-Seeking Student

**Type:** Primary Receiver  
**Priority:** P0

### Profile
- University student
- Heavy smartphone user
- Depends on mobile Internet for schoolwork and communication
- Student-budget conscious
- Comfortable with Android applications
- Has a trusted network of friends

### Situation
Mobile data can run out unexpectedly while he still needs Internet access for an assignment, communication, online payment, navigation, or another important task.

### Goal
Get temporary Internet access from someone he trusts without requiring that person to be physically nearby.

### Motivations
- Continue important online activities
- Avoid interruption
- Get help quickly
- Prefer a trusted person over an unknown public network

### Pain points
- Data finishes unexpectedly
- Buying more data may not be immediately possible
- Conventional hotspot sharing requires physical proximity
- Public Wi-Fi may be unavailable or untrusted
- Networking setup should not require technical knowledge

### Technology behavior
- Primarily Android/mobile
- Comfortable with simple app workflows
- Does not want to configure networking manually

### Expected journey
**Find → Request → Approval → Connect → Use → Disconnect**

### Success signal
The Receiver can request and use temporary connectivity with minimal technical knowledge while understanding connection status and usage.

---

## Persona 2 — Ama, the Trusted Provider

**Type:** Primary Provider  
**Priority:** P0

### Profile
- University student or young professional
- Has reliable mobile-data or Wi-Fi connectivity
- Frequently helps friends or family
- Values control over personal resources
- Uses Android regularly

### Situation
Ama has enough connectivity to help someone she trusts, but does not want unrestricted access or loss of visibility into consumption.

### Goal
Help a trusted person while retaining control over the connection.

### Motivations
- Help friends/family
- Make use of available connectivity
- Maintain trust
- Know who is connected
- Control duration and usage

### Pain points
- Physical hotspot sharing is inconvenient over distance
- Excessive data consumption
- Unknown people accessing her connection
- Battery consumption
- Need for an immediate kill switch

### Technology behavior
- Comfortable with smartphone permissions
- Expects clear controls
- Wants transparent usage information

### Expected journey
**Receive request → Verify → Approve → Set/confirm limits → Monitor → Stop**

### Success signal
The Provider feels confident that access is controlled and can terminate the session immediately.

---

## Persona 3 — Kojo, the Family/Long-Distance Helper

**Type:** Secondary Provider/Receiver  
**Priority:** P1

### Profile
- Young adult or working family member
- Has trusted relatives in different locations
- Uses mobile Internet regularly
- May alternate between Provider and Receiver roles

### Situation
A family member needs temporary connectivity while Kojo has Internet access, but they are not physically together.

### Goal
Provide or receive temporary connectivity remotely without complicated setup.

### Motivations
- Help family
- Stay connected during interruptions
- Reduce friction in remote assistance

### Pain points
- Distance prevents normal hotspot sharing
- Calls/messages do not themselves provide Internet access
- Networking configuration may be unfamiliar

### Expected journey
Simple trusted-contact workflow with strong identity and permission controls.

### Success signal
A trusted family member can be helped remotely without manually configuring networking infrastructure.

---

## Persona 4 — Yaw, the Mobile Traveler

**Type:** Secondary Receiver  
**Priority:** P1

### Profile
- Travels between cities or regions
- Depends on smartphone connectivity
- May temporarily experience poor or unavailable access
- Has trusted contacts elsewhere

### Situation
Yaw needs temporary connectivity while traveling and wants help from a trusted person who is not physically nearby.

### Goal
Restore useful connectivity quickly without relying on unfamiliar public networks.

### Motivations
- Navigation
- Communication
- Work/study
- Emergency access

### Pain points
- Network availability changes while traveling
- Public Wi-Fi can be unreliable
- Purchasing another plan may not be convenient immediately

### Success signal
A secure authorized session can be established when the underlying networks support it.

---

## Persona 5 — The Unwanted User

**Type:** Negative persona  
**Priority:** Security boundary

This persona represents people Linko should not optimize for.

### Profile
- Wants unauthorized connectivity
- Attempts to access Providers without consent
- May abuse bandwidth or infrastructure
- May seek to bypass carrier/ISP restrictions

### Linko response
- No unauthorized access
- Strong authentication
- Explicit Provider authorization
- Session authorization controls
- Rate limiting
- Blocking/reporting
- Abuse detection
- Immediate session termination

---

## Primary design personas

### Primary
1. **Kwame — Receiver**
2. **Ama — Provider**

### Secondary
3. **Kojo — Family/Long-Distance Helper**
4. **Yaw — Traveler**

### Negative
5. **Unwanted User**

## Persona decision matrix

| Persona | Role | Priority | MVP design target |
|---|---|---:|---|
| Kwame | Receiver | P0 | Yes |
| Ama | Provider | P0 | Yes |
| Kojo | Provider/Receiver | P1 | Consider |
| Yaw | Receiver | P1 | Consider |
| Unwanted User | Abuse case | Security | Defend against |

## Design implications

- First-run experience must be understandable to a non-networking expert.
- Provider identity and consent must be prominent.
- Receiver requests must be simple.
- Usage and session limits must be visible.
- Provider must have an immediate disconnect mechanism.
- Trust relationships must be understandable.
- Errors must explain real network limitations honestly.
- Security controls must not depend on users understanding networking protocols.

## Research validation plan

Validate these personas before major product-market decisions through:

- Student interviews
- Provider interviews
- Receiver interviews
- Surveys
- Prototype usability sessions
- Controlled beta behavior
- Privacy-conscious session analytics

Test whether assumed goals, problem frequency, trust expectations, usage limits, and willingness to provide connectivity match actual behavior. Personas remain revisable as evidence accumulates.

## Acceptance criteria

- [x] Primary Receiver persona defined
- [x] Primary Provider persona defined
- [x] Secondary personas defined
- [x] Negative persona defined
- [x] Goals and motivations documented
- [x] Pain points documented
- [x] Technology behavior documented
- [x] Design implications documented
- [x] Validation plan documented
- [x] Persona priorities defined

## Review checklist

- [x] Personas map to the target-user segments in Phase 1.3
- [x] Provider and Receiver roles are explicitly distinguished
- [x] Negative/security persona is included
- [x] MVP priorities are identified
- [x] Personas are treated as hypotheses rather than validated research
- [x] No later SDLC phase has been started

## Approval gate

**Project-owner approval:** PENDING

This deliverable must receive explicit project-owner approval before it is marked COMPLETE and before Phase 1.5 begins.

## Next deliverable after approval

**Phase 1.5 — User Journeys**

Phase 1 remains **IN PROGRESS**. Phase 2 remains locked.
