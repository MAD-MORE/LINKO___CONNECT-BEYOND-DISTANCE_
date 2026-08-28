# Android Security Model — Linko

## Layers of Security

```
┌─────────────────────────────────────────────┐
│  Layer 1: User Authentication (Supabase)    │
│  Email + password → Supabase JWT            │
├─────────────────────────────────────────────┤
│  Layer 2: Device Identity (Linko)           │
│  Permanent device ID + HMAC-signed JWT      │
├─────────────────────────────────────────────┤
│  Layer 3: Session Authorization             │
│  Short-lived session key (per-session)      │
├─────────────────────────────────────────────┤
│  Layer 4: Tunnel Encryption                 │
│  AES-256-GCM (end-to-end, relay is blind)  │
├─────────────────────────────────────────────┤
│  Layer 5: Android Keystore                  │
│  Hardware-backed key storage where avail.  │
└─────────────────────────────────────────────┘
```

---

## Device Identity

### LinkoDeviceIdentity

The device ID is a UUID generated on first launch and persisted in `EncryptedSharedPreferences`. It is permanent for the life of the app installation.

```
Storage location: EncryptedSharedPreferences("linko_identity")
Key: "device_id"
Value: UUID v4 string (e.g. "a1b2c3d4-...")
Backup: Excluded from Android Auto Backup (privacy)
```

This ID is registered with the control plane on first run. The control plane issues a device JWT bound to this ID.

### Device JWT Structure

```json
{
  "sub": "<supabase-user-id>",
  "deviceId": "<linko-device-id>",
  "iat": 1234567890,
  "exp": 1234567890
}
```

JWTs are HMAC-SHA256 signed with `LINKO_JWT_SECRET`. Expiry: 30 days. The app stores the device JWT in EncryptedSharedPreferences.

---

## LinkoKeyManager (Android Keystore)

`LinkoKeyManager` manages cryptographic keys using the Android Keystore system, which on modern devices (API 28+) stores key material in hardware-backed secure storage (StrongBox or TEE).

### Keys managed

| Key alias | Algorithm | Purpose | Backed by |
|---|---|---|---|
| `linko_identity_key` | AES-256-GCM | Encrypt local identity data | Keystore (HW where available) |
| `linko_session_key_<id>` | AES-256-GCM | Session tunnel encryption | Keystore |
| `linko_prefs_key` | AES-256-GCM | EncryptedSharedPreferences master key | Keystore |

### Key generation policy

```kotlin
// Example: identity key generation
val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
keyGen.init(
    KeyGenParameterSpec.Builder("linko_identity_key",
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .setUserAuthenticationRequired(false) // No biometric needed for background ops
        .setRandomizedEncryptionRequired(true)
        .build()
)
keyGen.generateKey()
```

---

## LinkoSecurePrefs

A thin wrapper over `EncryptedSharedPreferences` for storing sensitive values:

- Device JWT
- Device ID
- Cached user email
- Server URLs (to prevent hostname injection)

```kotlin
object LinkoSecurePrefs {
    private const val PREFS_NAME = "linko_secure"
    
    fun getDeviceJwt(context: Context): String?
    fun setDeviceJwt(context: Context, jwt: String)
    fun clearDeviceJwt(context: Context)
    
    fun getDeviceId(context: Context): String?
    fun setDeviceId(context: Context, id: String)
    
    fun clearAll(context: Context)  // Called on sign-out
}
```

---

## Session Key Lifecycle

```
Control Plane issues 32-byte session key
        │
        │ HTTPS (TLS 1.3)
        ▼
Android receives key via GET /v1/sessions/:id/tunnel
        │
        │ Stored in memory only (never written to disk)
        ▼
EncryptedDatagramTunnel uses key for AES-GCM
        │
        ▼
Session ends (disconnect, revoke, expire)
        │
        ▼
Key zeroed in memory (ByteArray.fill(0))
Control plane marks key as revoked
```

Session keys are **never persisted** to disk. If the app is killed and restarted, a new session must be created.

---

## What is NOT Stored on Device

| Item | Why not stored |
|---|---|
| User password | Never — Supabase handles auth, password not needed after login |
| Session tunnel key | Memory-only; revoked on session end |
| Provider's network credentials | Never — Linko does not inspect or store Provider's network auth |
| Receiver's traffic content | Never — Linko does not log or store traffic |
| Friends' private data | Only public profile (display name, device ID) cached |

---

## Secure Coding Rules (Android)

1. Never log device JWT, session key, or user email at `Log.d/i/w/e` level in release builds
2. Never pass secrets via Android Intent extras (use in-memory or EncryptedSharedPreferences)
3. Use `FLAG_SECURE` on all auth screens to prevent screenshot capture
4. Clear sensitive strings from memory after use (use `CharArray` instead of `String` for passwords)
5. The `INTERNET` permission is required; no other dangerous permissions beyond VPN and notifications
6. VPN traffic file descriptor must be closed in `finally` blocks to prevent fd leaks

---

## Android Backup Exclusion

The following files are excluded from Android Auto Backup and cloud backup:

```xml
<!-- res/xml/backup_rules.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="linko_secure.xml" />
    <exclude domain="sharedpref" path="linko_identity.xml" />
</full-backup-content>
```

Device JWTs and device IDs must not be restored to a different device — they are device-specific credentials.
