# Google Play Launch Checklist — Linko

## Pre-Submission Checklist

### Account Setup
- [ ] Google Play developer account created ($25 one-time fee)
- [ ] Developer name set to [YOUR COMPANY/NAME]
- [ ] Payment profile configured

---

### App Signing
- [ ] Release keystore generated:
  ```bash
  keytool -genkey -v -keystore linko-release.jks \
    -alias linko -keyalg RSA -keysize 2048 -validity 10000
  ```
- [ ] Keystore backed up in secure offline storage (NOT in the repository)
- [ ] `local.properties` configured with keystore path and passwords:
  ```
  KEYSTORE_PATH=/path/to/linko-release.jks
  KEY_ALIAS=linko
  KEYSTORE_PASSWORD=...
  KEY_PASSWORD=...
  ```
- [ ] Release APK/AAB signed successfully:
  ```bash
  cd android && ./gradlew bundleRelease
  ```
- [ ] Enrolled in Google Play App Signing (recommended — Google holds the upload key)

---

### Store Listing
- [ ] App title: "Linko — Connect Beyond Distance" (≤ 30 chars: "Linko")
- [ ] Short description (≤ 80 chars): "Share your Internet connection securely with trusted friends"
- [ ] Full description (≤ 4000 chars): [Write full description — see docs/launch/store-description.md]
- [ ] App icon: 512×512 PNG (high-res)
- [ ] Feature graphic: 1024×500 PNG
- [ ] Screenshots: minimum 2 phone screenshots (1080×1920 or similar)
  - [ ] Onboarding screen
  - [ ] Friends list
  - [ ] Active connection screen
  - [ ] Provider sharing screen
- [ ] Category: Tools
- [ ] Tags: VPN, network sharing, connectivity, internet sharing

---

### Content Rating
- [ ] Content rating questionnaire completed (IARC)
  - Violence: None
  - Sexual content: None
  - Language: None
  - Controlled substances: None
- [ ] Expected rating: Everyone (E)

---

### Data Safety Form (Required by Google)
- [ ] Data collected section:
  - Email address: collected, required, not shared with third parties
  - Device ID: collected, required, encrypted in transit
  - Network info: usage data (bytes), required for billing display
- [ ] Data NOT collected: precise location, contacts, photos/media, financial info
- [ ] Security practices declared:
  - Data encrypted in transit: Yes (HTTPS + AES-256-GCM)
  - Data deleted on account deletion: Yes
  - Users can request deletion: Yes (in-app Settings → Delete Account)
  - Independent security review: No (pending for post-beta)

---

### VPN Permission Disclosure (Critical for Google Play)
- [ ] Prominent disclosure in app before VPN permission is requested
- [ ] App description clearly states VPN is used for connectivity sharing (not privacy/anonymity)
- [ ] `uses-permission android:name="android.permission.BIND_VPN_SERVICE"` declared correctly
- [ ] VPN permission dialog uses Android's built-in `VpnService.prepare()` dialog (compliant)
- [ ] **Linko does NOT claim to be a security/privacy VPN** — this distinction is critical for Play Store compliance

---

### Technical Requirements
- [ ] `minSdk = 26` (Android 8.0) — confirmed
- [ ] `targetSdk = 35` — confirmed (update to latest each year)
- [ ] `compileSdk = 36` — confirmed
- [ ] App built as Android App Bundle (.aab) for Play Store
- [ ] ProGuard/R8 enabled for release build
- [ ] No debug logging in release build
- [ ] No test code in production APK

---

### Legal
- [ ] Privacy Policy URL live and linked in Play Console
- [ ] Terms of Service URL live (or use Privacy Policy URL for both)
- [ ] Privacy Policy mentions VPN data usage
- [ ] Privacy Policy mentions Google Play Billing (if monetized)

---

### Final Verification
- [ ] Install release APK on at least 2 real Android devices
- [ ] Complete full user flow on release build (not debug):
  - Sign up → verify → add friend → request connection → approve → connect → disconnect
- [ ] Verify no crash on Android 8.0 (minSdk)
- [ ] Verify notification appears during Provider session
- [ ] Verify VPN connection indicator appears in Android status bar during Receiver session
- [ ] Test "Delete Account" flow works end-to-end

---

### Submission
- [ ] Upload signed .aab to Play Console
- [ ] Set up Internal Testing track first (test with up to 100 users)
- [ ] After internal testing: promote to Closed Testing (beta) track
- [ ] After beta validation: promote to Production (phased rollout — start with 10%)

---

## Post-Launch Monitoring (First 48 Hours)
- [ ] Monitor Play Console for crash reports
- [ ] Monitor backend error rate (`/metrics`)
- [ ] Monitor relay health
- [ ] Respond to first user reviews
