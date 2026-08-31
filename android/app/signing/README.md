# Release Signing Guide — Linko Android

> ⚠️ **CRITICAL: Never commit the keystore file or passwords to version control.**

---

## Generate a Release Keystore

Run this once. Store the output file securely offline (e.g. encrypted USB drive, password manager).

```bash
keytool -genkey -v \
  -keystore linko-release.jks \
  -alias linko \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Linko, OU=Mobile, O=[Your Company], L=[City], S=[State], C=[Country Code]"
```

When prompted, set a strong password for both the keystore and the key alias.

**Back up the `.jks` file and passwords in at least 2 secure locations before proceeding.**

---

## Configure Signing in the Build

Add the following to `android/local.properties` (this file is gitignored):

```properties
LINKO_KEYSTORE_PATH=/absolute/path/to/linko-release.jks
LINKO_KEY_ALIAS=linko
LINKO_KEYSTORE_PASSWORD=your-keystore-password
LINKO_KEY_PASSWORD=your-key-password
```

Then update `android/app/build.gradle.kts` to include:

```kotlin
android {
    signingConfigs {
        create("release") {
            val props = java.util.Properties().apply {
                load(rootProject.file("local.properties").inputStream())
            }
            storeFile = file(props.getProperty("LINKO_KEYSTORE_PATH", ""))
            storePassword = props.getProperty("LINKO_KEYSTORE_PASSWORD", "")
            keyAlias = props.getProperty("LINKO_KEY_ALIAS", "linko")
            keyPassword = props.getProperty("LINKO_KEY_PASSWORD", "")
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
```

---

## Build Signed Release Bundle

```bash
cd android
./gradlew bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab
```

---

## Google Play App Signing (Recommended)

Enroll in Google Play App Signing so Google manages the final signing key:
1. Upload your `.jks` as the "upload key" in Play Console → Setup → App signing
2. Google re-signs your AAB with their managed key before distribution
3. Even if your upload key is lost, Google can still distribute the app

---

## CI/CD Signing (GitHub Actions)

Store signing credentials as GitHub Secrets:

```bash
# Encode keystore as base64
base64 -i linko-release.jks | pbcopy  # macOS
# Add to GitHub Secrets as LINKO_KEYSTORE_BASE64
```

In the workflow:
```yaml
- name: Decode keystore
  run: |
    echo "${{ secrets.LINKO_KEYSTORE_BASE64 }}" | base64 --decode > android/app/linko-release.jks
- name: Build release
  run: ./gradlew bundleRelease
  env:
    LINKO_KEYSTORE_PATH: ${{ github.workspace }}/android/app/linko-release.jks
    LINKO_KEYSTORE_PASSWORD: ${{ secrets.LINKO_KEYSTORE_PASSWORD }}
    LINKO_KEY_ALIAS: linko
    LINKO_KEY_PASSWORD: ${{ secrets.LINKO_KEY_PASSWORD }}
```
