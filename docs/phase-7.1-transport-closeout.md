# Phase 7.1 — Real Transport

Status: IMPLEMENTED, LIVE VERIFICATION PENDING

Implemented:
- Receiver IPv4 TUN packet capture.
- AES-GCM packet encryption/decryption.
- Authenticated WSS relay transport.
- Bidirectional relay packet delivery.
- Provider userspace IPv4 gateway for TCP and UDP.
- Reverse TCP/UDP packet reconstruction and relay emission.
- Per-session ECDH public-key exchange.
- HKDF-SHA256 session-key derivation from the on-device ECDH secret.
- Idempotent session creation.
- Provider-mode relay service integration.

Not claimed as verified:
- Two physical Android devices completing an Internet-browsing session.
- Production relay/signaling deployment.
- Full TCP behavior under retransmission, congestion, fragmentation, and network switching.
- IPv6/ICMP forwarding.

The implementation fails closed for unsupported protocols and missing production credentials.
