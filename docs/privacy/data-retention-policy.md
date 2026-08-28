# Data Retention Policy — Linko

**Version:** 1.0  
**Effective date:** [DATE]

---

## Scope

This policy defines how long Linko stores each category of personal data and how it is deleted when no longer needed or when requested.

---

## Retention Schedule

| Data Category | Retention Period | Deletion Method |
|---|---|---|
| Account (email, password hash) | Until account deletion | Supabase admin API |
| Device records | Until account deletion or device revocation | DELETE from devices table |
| Session metadata | 12 months from session end | Scheduled DELETE |
| Usage records | 12 months from recorded_at | Scheduled DELETE |
| Security events | 90 days | Scheduled DELETE |
| Blocked user records | 24 hours to 30 days (configurable) | Automatic expiry |
| Friend relationships | Until account deletion or unfriend | Supabase DB delete |
| Payment records | 7 years (legal requirement for financial records) | Archived, not deleted |
| Subscription records | Until cancelled + 1 year | Archived |

---

## Deletion Propagation

When a user deletes their account via **Settings → Delete Account** or via the API endpoint `DELETE /v1/account`:

1. All `usage_records` for their devices are deleted
2. All `sessions` involving their devices are terminated and deleted
3. All `security_events` for their user ID are deleted
4. All `blocked_users` records for their user ID are deleted
5. All `devices` registered to their user ID are deleted
6. Their Supabase user account (email, password) is deleted
7. Friend relationships are deleted (Supabase DB)

**Timeline:** Deletion is initiated immediately. All data is removed within 30 days.

---

## Automated Purge Jobs

The following SQL should be run monthly as a maintenance job:

```sql
-- Remove expired session data (> 12 months old)
DELETE FROM sessions WHERE ended_at < NOW() - INTERVAL '12 months';

-- Remove old usage records
DELETE FROM usage_records WHERE recorded_at < NOW() - INTERVAL '12 months';

-- Remove old security events
DELETE FROM security_events WHERE created_at < NOW() - INTERVAL '90 days';

-- Remove expired blocks
DELETE FROM blocked_users WHERE expires_at < NOW();
```

**Recommended:** Schedule as a Fly.io cron job or pg_cron job.

---

## Traffic Content

Linko **never stores** the content of user Internet traffic. The relay nodes forward encrypted ciphertext only. No traffic content is logged, stored, or accessible to Linko at any time.

---

## Backup Retention

Database backups (Fly Postgres snapshots) are retained for:
- **Daily snapshots:** 7 days
- **Weekly snapshots:** 4 weeks

Backup data is subject to the same retention policies as live data.

---

## Data Requests

Users can request their data export or deletion:
- **In-app:** Settings → Privacy → Export My Data / Delete Account
- **By email:** privacy@linko.app

Requests are processed within 30 days (GDPR requirement) or 45 days (CCPA).
