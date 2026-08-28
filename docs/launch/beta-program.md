# Beta Program — Linko

> ⚠️ **HUMAN ACTION REQUIRED** — Recruiting beta testers requires your direct effort.

## Overview

The Linko beta program validates the MVP on real devices before the full Google Play launch. Beta testers get early access in exchange for structured feedback.

---

## Beta Goals

1. Validate that the Provider→Receiver tunnel works on real devices across real networks
2. Identify UI/UX friction points
3. Stress-test the backend and relay with real usage
4. Collect feedback on the connection request/approval flow
5. Identify Android device-specific compatibility issues

---

## Beta Cohort (Target: 20–50 testers)

| Cohort | Size | Priority |
|---|---|---|
| Friends and family (known, trusted) | 10 | First week |
| Tech-savvy early adopters | 20 | Week 2 |
| Diverse device/carrier coverage | 20 | Week 3 |

**Device targets to cover:**
- Android 8.0 (API 26) — oldest supported
- Android 12, 13, 14 — most common in market
- Samsung Galaxy (One UI) — largest Android OEM
- Pixel — stock Android
- OnePlus / Xiaomi — MIUI/OxygenOS (aggressive battery optimization)

---

## Enrollment Process (Human Action Steps)

1. **Create a Google Play Closed Testing track** in Play Console
2. **Create a Google Group** (e.g. linko-beta@googlegroups.com) for tester management
3. **Share the enrollment link** with selected testers
4. **Collect tester email addresses** and add to the testing group
5. Testers opt in via the Play Store beta link

---

## Feedback Collection

### In-App Feedback (Phase 24 implementation)
Add a "Send Feedback" button to the Settings screen that opens a pre-filled email:
```
Subject: Linko Beta Feedback - [version]
To: beta@linko.app
```

### Bug Report Template
See `.github/ISSUE_TEMPLATE/bug_report.md`

### Structured Survey (after 2 weeks)
Send beta testers a Google Form with:
1. How many times did you successfully connect? (0 / 1–3 / 4–10 / 10+)
2. Did any connections fail? If yes, what happened?
3. How long did it typically take to establish a connection?
4. Did the app crash?
5. What's the #1 thing you'd improve?
6. Would you recommend Linko to a friend? (1–10)
7. What Android device and version are you using?
8. What carrier/network type did you test on?

---

## Beta Success Criteria

Before promoting to Production:
- [ ] ≥ 15 testers have successfully completed at least 1 end-to-end connection
- [ ] Zero P0 crashes reported
- [ ] P99 session establishment time < 15 seconds (measured from feedback)
- [ ] No reports of data leaking outside the tunnel
- [ ] All reported bugs with severity High+ addressed
- [ ] NPS score ≥ 7/10 average

---

## Beta Feedback Remediation

Triage all feedback into:
| Priority | Description | Action |
|---|---|---|
| P0 | Data leak, security issue, complete failure | Fix before Production |
| P1 | Connection fails frequently, crash on common device | Fix before Production |
| P2 | Poor UX, minor bug, cosmetic issue | Fix if time allows |
| P3 | Nice-to-have, future feature | Log for backlog |
