# Linko Economy — Provider Incentive Model

## Overview

The Linko Economy rewards Providers for sharing their Internet connection. This creates a sustainable marketplace where:
- Providers earn credits that offset their own Linko subscription costs
- Receivers benefit from more available Providers in their network
- Linko benefits from higher Provider participation rates

---

## MVP Stance (Phase 22)

**The MVP does NOT implement the Linko Economy.** It is deferred until:
1. The core tunnel is working reliably on real devices
2. Monetization (Phase 22) is live with paid subscribers
3. Sufficient data exists to model Provider incentive costs accurately

This document defines the intended design for post-MVP implementation.

---

## Credit Earning (Provider)

| Action | Credits earned |
|---|---|
| Sharing 1 GB of relay traffic | 10 credits |
| Sharing 1 GB of direct traffic | 5 credits |
| Completing 10 sessions in a month | Bonus: 50 credits |
| Referring a new user (who completes 3 sessions) | 200 credits |

---

## Credit Redemption (Provider)

| Redemption | Credits required |
|---|---|
| 1 month Pro plan | 500 credits |
| 1 month Unlimited plan | 1,000 credits |
| Extra 5 GB relay for this month | 200 credits |

---

## Credit Value

```
500 credits = 1 month Pro = $4.99 value
1 credit = $0.00998 (~$0.01)

Provider earns 10 credits/GB shared via relay
→ earns $0.10 per GB shared
→ relay costs Linko $0.17/GB
→ Provider incentive adds $0.10 to the cost
→ Total cost: $0.27/GB (vs. $0.17 base)
→ Still profitable at Pro tier pricing
```

---

## Provider Eligibility

To earn credits, a Provider must:
1. Be on the Pro or Unlimited plan (ensures skin in the game)
2. Have a stable connection (< 10% packet loss during session)
3. Not have been flagged for abuse in the last 30 days
4. Complete sessions of at least 5 minutes

---

## Anti-Gaming Controls

- Credits are earned per unique Receiver device (not per session)
- Same Receiver→Provider pair earns credits max once per 24 hours
- Suspicious patterns (high volume, new accounts, same network) are reviewed
- Credits expire after 12 months if not redeemed

---

## Implementation Requirements (Post-MVP)

- `credits` table in database (user_id, amount, reason, expires_at)
- Credit earning: triggered by `usage_records` insertion (backend calculates)
- Credit redemption: tied to Play Billing (credit used instead of payment)
- Provider dashboard: shows earnings, redeemable balance, session history
- Fraud review queue: flagged credit transactions for human review
