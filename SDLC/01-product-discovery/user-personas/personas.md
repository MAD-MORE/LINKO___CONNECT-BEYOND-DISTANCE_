# Phase 1.4 — User Personas

## Status

**CURRENT — IN PROGRESS**

## Persona methodology

These personas are initial working hypotheses derived from the target-user definition. They are not claims that these exact individuals exist. They must be validated through interviews, surveys, prototype testing, and beta evidence before being treated as research-validated personas. Research-backed personas are intended to represent meaningful user patterns and guide product decisions. citeturn0search1turn0search3

## Persona 1 — Kwame, the Connectivity-Seeking Student

**Type:** Primary Receiver persona  
**Priority:** P0

### Profile

- University student
- Heavy smartphone user
- Depends on mobile Internet for schoolwork and communication
- Often operates within a student budget
- Comfortable with Android applications
- Has a trusted network of friends

### Situation

Kwame's mobile data can run out unexpectedly while he still needs Internet access for an assignment, communication, online payment, navigation, or another important task.

### Primary goal

Get temporary Internet access from someone he trusts without requiring that person to be physically nearby.

### Motivations

- Continue important online activities
- Avoid unnecessary interruption
- Get help quickly
- Use a trusted person rather than an unknown public network

### Pain points

- Data finishes at inconvenient times
- Buying more data may not always be immediately possible
- Conventional hotspot sharing requires physical proximity
- Public Wi-Fi may be unavailable or untrusted
- He needs a simple connection process

### Technology behavior

- Primarily Android/mobile
- Comfortable with simple app workflows
- Does not want to configure networking manually

### Linko expectation

**Find → Request → Wait for approval → Connect → Use → Disconnect**

### Success signal

Kwame can request and receive temporary connectivity with minimal technical knowledge and understands the connection status and usage.

---

## Persona 2 — Ama, the Trusted Provider

**Type:** Primary Provider persona  
**Priority:** P0

### Profile

- University student or young professional
- Has a reliable mobile-data or Wi-Fi connection
- Frequently helps friends or family
- Values control over personal resources
- Uses Android regularly

### Situation

Ama has enough connectivity to help someone she trusts, but she does not want to hand over unrestricted access or lose visibility into her data consumption.

### Primary goal

Help a trusted person while retaining complete control over the connection.

### Motivations

- Help friends/family
- Make use of available connectivity
- Maintain trust
- Know exactly who is connected
- Control duration and data usage

### Pain points

- Physical hotspot sharing is inconvenient when people are far apart
- Concern about excessive data consumption
- Concern about unknown people accessing her connection
- Concern about battery consumption
- Wants an immediate kill switch

### Technology behavior

- Comfortable with smartphone permissions
- Expects clear controls
- Wants transparent usage information

### Linko expectation

**Receive request → Verify person → Approve → Set/confirm limits → Monitor → Stop**

### Success signal

Ama feels confident that she controls access and can terminate the session immediately.

---

## Persona 3 — Kojo, the Family/Long-Distance Helper

**Type:** Secondary Provider/Receiver persona  
**Priority:** P1

### Profile

- Young adult or working family member
- Has trusted relatives in different cities or locations
- Uses mobile Internet regularly
- May alternate between Provider and Receiver roles

### Situation

A family member needs temporary connectivity while Kojo has Internet access, but they are not physically together.

### Primary goal

Provide or receive temporary connectivity remotely without complicated technical setup.

### Motivations

- Help family
- Stay connected during emergencies or interruptions
- Reduce friction in remote assistance

### Pain points

- Distance prevents normal hotspot sharing
- Phone calls or messaging cannot themselves provide Internet access
- May not understand networking configuration

### Linko expectation

A simple trusted-contact workflow with strong identity and permission controls.

### Success signal

Kojo can help a trusted family member remotely without manually configuring networking infrastructure.

---

## Persona 4 — Yaw, the Mobile Traveler

**Type:** Secondary Receiver persona  
**Priority:** P1

### Profile

- Travels between cities or regions
- Depends on smartphone connectivity
- May temporarily experience poor or unavailable access
- Has trusted contacts who may be elsewhere

### Situation

Yaw needs temporary connectivity while traveling and wants help from a trusted person who is not physically nearby.

### Primary goal

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

Yaw can establish a secure authorized session when the underlying networks support it.

---

## Persona 5 — The Unwanted User

**Type:** Negative persona  
**Priority:** Security boundary

This persona represents people Linko should **not** optimize for.

### Profile

- Wants unauthorized connectivity
- Attempts to access Providers without consent
- May attempt to abuse bandwidth or infrastructure
- May seek to bypass carrier/ISP restrictions

### Why this persona exists

Negative personas help teams avoid accidentally designing features that attract users who conflict with the product's security, trust, and business objectives.

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

The MVP should prioritize a small number of primary personas rather than attempting to satisfy every possible user segment simultaneously. UX guidance commonly recommends a small set of primary personas and using research to validate them. citeturn0search1

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

The personas establish several requirements for later phases:

- The first-run experience must be understandable to a non-networking expert.
- Provider identity and consent must be prominent.
- Receiver connection requests must be simple.
- Usage and session limits must be visible.
- Provider must have an immediate disconnect mechanism.
- Trust relationships must be understandable.
- Error messages must explain real network limitations without pretending that Linko can guarantee connectivity.
- Security controls must not depend on users understanding networking protocols.

## Research validation plan

Before final product-market decisions, validate these personas using:

- Student interviews
- Provider interviews
- Receiver interviews
- Surveys
- Prototype usability sessions
- Controlled beta behavior
- Session analytics with appropriate privacy controls

Research should test whether the assumed goals, frequency of the problem, trust expectations, usage limits, and willingness to provide connectivity match actual behavior. Personas should remain revisable as evidence accumulates. citeturn0search1turn0search11

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

## Next deliverable

**Phase 1.5 — User Journeys**

Phase 1 remains **IN PROGRESS**. Phase 2 remains locked.
