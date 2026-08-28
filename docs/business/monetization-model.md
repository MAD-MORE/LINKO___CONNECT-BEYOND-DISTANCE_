# Monetization Model — Linko

## Strategy: Freemium with Relay Usage Caps

The MVP launches with a freemium model. Revenue comes from users who need more relay bandwidth than the free tier allows. Direct-path sessions (no relay) are always free and unlimited.

---

## Plans

| Plan | Price | Relay bandwidth/month | Direct sessions | Support |
|---|---|---|---|---|
| **Free** | $0 | 1 GB | Unlimited | Community |
| **Pro** | $4.99/month | 10 GB | Unlimited | Email |
| **Unlimited** | $9.99/month | Unlimited | Unlimited | Priority email |

---

## Rationale

- **Free tier (1 GB):** Enough for casual use (email, light browsing). Creates viral adoption because users can try Linko before paying.
- **Pro tier ($4.99):** Targets users who travel, use mobile data regularly, or share connections with family. 10 GB = ~20 typical relay sessions/month.
- **Unlimited tier ($9.99):** For power users and digital nomads. Removes anxiety about tracking bandwidth.

---

## Revenue Model

### Unit Economics (at scale)

| Metric | Value |
|---|---|
| Relay cost per GB | ~$0.17 (Fly.io) → drops to $0.05+ with volume |
| Free tier cost per active user | ~$0.17/month (if using full 1 GB) |
| Pro tier margin | ($4.99 - 10 × $0.17) = $3.29/user/month (66%) |
| Unlimited tier margin | Depends on avg usage; break-even at ~50 GB/user |

### Break-even Analysis

At 500 MAU with 10% paid conversion:
- Free users (450): infrastructure cost ~$0.26 × 450 = $117
- Pro users (40): revenue $4.99 × 40 = $200; cost ~$68
- Unlimited users (10): revenue $9.99 × 10 = $100; cost ~$85 (worst case)
- **Net: +$230 - $270 ≈ breakeven to marginally profitable at 500 MAU**

Profitable at ~1,000 MAU with 10% paid conversion.

---

## Payment Processing

- **Google Play Billing** for Android (required for in-app purchases on Google Play)
- Subscription management handled by Google Play
- Backend validates purchase tokens via Google Play Developer API
- No direct credit card processing in MVP (Google Play handles it)

---

## Free Trial

- 30-day free Pro trial on signup (optional — adds conversion complexity; evaluate post-beta)
- OR: No trial — free tier is generous enough to drive organic upgrade

---

## Future Revenue Streams (Post-MVP)

| Stream | Description |
|---|---|
| Provider credits | Providers earn credits for sharing — redeemable for Pro plan credits |
| Team/Family plans | Multiple users share a plan quota |
| B2B / Enterprise | Corporate data sharing, IoT device connectivity |
| API access | Paid API for developers building on Linko protocol |

---

## Anti-Abuse on Free Tier

- 1 GB relay hard cap per calendar month
- Overage: session creation blocked; user prompted to upgrade
- Rate limits prevent gaming the system with multiple accounts
- Device ID binding prevents easy account cycling
