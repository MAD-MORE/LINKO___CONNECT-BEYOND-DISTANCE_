# LINKO — Connect Beyond Distance

Internet data sharing beyond distance.

## Current phase

**Phase 5.1 — Production implementation**

The approved UI is frozen and must be implemented without redesigning it. The visual source of truth is `Implement Prototype.zip` with SHA-256:

`6379a3b2af1b4f6e048addaed08322d7f6fcf555bffe2fca12a5b1e974427ef6`

See [`docs/phase-5.1-ui-reference.md`](docs/phase-5.1-ui-reference.md) for the complete screen inventory and exit gate.

## Product direction

LINKO lets trusted friends share an internet connection remotely. The production architecture uses Android `VpnService` on the receiving device, a secure tunnel, signaling, direct peer connectivity where possible, and relay fallback when a direct path cannot be established.
