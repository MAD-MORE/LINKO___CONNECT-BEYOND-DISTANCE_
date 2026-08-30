# LINKO CI signing setup

LINKO release/update builds use the existing signing identity so installed builds can be updated in place.

## Required GitHub Actions secrets

Configure these repository secrets under **Settings → Secrets and variables → Actions**:

- `LINKO_KEYSTORE_BASE64` — Base64-encoded canonical LINKO keystore. Do not commit the keystore itself.
- `LINKO_KEYSTORE_PASSWORD` — keystore/store password.
- `LINKO_KEY_ALIAS` — signing key alias. The production workflow defaults to `linko` when this secret is omitted.
- `LINKO_KEY_PASSWORD` — private-key password.

## CI behavior

The workflow:

1. Requires the keystore, store password, key password, and alias before a non-PR release build.
2. Recovers common Base64 transport variants without replacing the signing material.
3. Validates JKS, PKCS12, and JCEKS candidates with the configured store password.
4. Normalizes PKCS12/JCEKS input to JKS for the Android signing configuration.
5. Verifies the configured alias before signing.
6. Removes the temporary signing keystore after the job, including failed jobs.

A replacement signing key must **not** be generated in CI. Replacing the key would prevent an existing LINKO installation from accepting the update as an in-place upgrade.

## Security

Never put the keystore, passwords, Base64 value, or private key material in source control, workflow logs, issues, pull requests, or chat messages. Only the secret names belong in repository documentation.
