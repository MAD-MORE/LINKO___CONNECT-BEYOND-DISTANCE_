# Privacy Policy — Linko (Template)

> ⚠️ **LEGAL REVIEW REQUIRED** — This is a template. Before publishing, have it reviewed by a qualified lawyer familiar with GDPR, CCPA, and Google Play Store policies.

**Last updated:** [DATE]  
**Effective date:** [DATE]

---

## 1. Who We Are

Linko ("we", "our", "us") is operated by [YOUR LEGAL ENTITY NAME], registered at [ADDRESS].

Contact for privacy matters: privacy@linko.app

---

## 2. What Linko Does

Linko is an Android application that allows users ("Providers") to voluntarily share their Internet connection with authorized contacts ("Receivers") over an encrypted tunnel.

---

## 3. Data We Collect

### 3.1 Account Data
- Email address (used for authentication via Supabase)
- Password (hashed by Supabase — we never store plaintext passwords)
- Display name (optional, chosen by you)

### 3.2 Device Data
- Device identifier (a UUID generated on app install, specific to your Linko installation)
- Device name (you provide this during setup)
- Device role (Provider, Receiver, or both)
- Device last-seen timestamp (for online/offline presence)

### 3.3 Session Metadata
- Session start time and end time
- Total bytes uploaded and downloaded per session
- Session status (requested, approved, connected, revoked, etc.)

**We do NOT record, store, or inspect the content of your Internet traffic.** The encrypted tunnel data passes through relay nodes that cannot decrypt it. Session content is never stored anywhere in our system.

### 3.4 Security Events
- Failed authentication attempts (for abuse detection)
- Connection request rejections (for spam detection)
- Rate limit violations

### 3.5 Friend Relationships
- The user IDs of people you have added as friends on Linko

---

## 4. How We Use Your Data

| Data | Purpose | Legal basis |
|---|---|---|
| Email + password | Authentication | Contract performance |
| Device identifier | Session authorization | Contract performance |
| Session metadata | Billing, usage display, abuse prevention | Contract performance / Legitimate interests |
| Security events | Abuse prevention, security | Legitimate interests |
| Friend relationships | Enable friend-to-friend connections | Contract performance |

We do NOT sell your personal data to third parties.  
We do NOT use your data for advertising.

---

## 5. Data Sharing

We share data with:

- **Supabase** (auth provider, EU/US hosting) — stores your email, password hash, and friend relationships
- **Fly.io** (infrastructure provider) — hosts our backend and relay servers; processes session metadata but not traffic content
- **Google** (Google Play) — if you purchase a subscription, Google processes payment data per Google's privacy policy

We do not share your data with any other third parties.

---

## 6. Data Retention

| Data | Retention period |
|---|---|
| Account data | Until account deletion |
| Session metadata | 12 months |
| Usage records | 12 months |
| Security events | 90 days |
| Deleted account data | Purged within 30 days of deletion request |

---

## 7. Your Rights (GDPR)

If you are in the European Economic Area, you have the right to:

- **Access** — request a copy of your personal data (via Settings → Export My Data)
- **Rectification** — correct inaccurate data
- **Erasure** — delete your account and all associated data (via Settings → Delete Account)
- **Portability** — receive your data in a machine-readable format
- **Objection** — object to processing based on legitimate interests
- **Restriction** — request restriction of processing

To exercise these rights: privacy@linko.app

You have the right to lodge a complaint with your local data protection authority.

---

## 8. Your Rights (CCPA / California)

California residents have the right to:
- Know what personal information is collected
- Know whether personal information is sold or disclosed (it is not)
- Opt out of sale of personal information (we do not sell data)
- Request deletion of personal information
- Non-discrimination for exercising your privacy rights

---

## 9. Children

Linko is not intended for users under 13 (or 16 in the EU). We do not knowingly collect data from children.

---

## 10. Security

We use industry-standard security practices:
- HTTPS/TLS for all API communications
- AES-256-GCM encryption for tunnel traffic
- Android Keystore for sensitive data storage
- Session keys revoked immediately on session termination
- No traffic content ever stored

---

## 11. Changes to This Policy

We will notify you of material changes via in-app notification or email. Continued use after the effective date constitutes acceptance.

---

## 12. Contact

Privacy inquiries: privacy@linko.app  
Mailing address: [YOUR ADDRESS]
