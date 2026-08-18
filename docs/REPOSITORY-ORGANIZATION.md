# Linko Repository Organization Standard

## Purpose

Linko must remain clean, predictable, modular, and easy for humans and AI agents to navigate.

**Nothing should be scattered.** Every file belongs to the category that owns it.

This document is a mandatory repository organization standard.

## Canonical top-level structure

```text
LINKO___CONNECT-BEYOND-DISTANCE_/
├── android/
│   └── Linko Android application and Android-specific modules
├── backend/
│   └── API, authentication, users, friends, sessions, signaling, usage, billing, admin
├── relay/
│   └── Relay and tunneling infrastructure
├── shared/
│   └── Shared contracts, models, protocol definitions
├── docs/
│   └── General technical/product documentation
├── infra/
│   └── Deployment, infrastructure, environments, operational configuration
├── tests/
│   └── Cross-component, integration, end-to-end and test infrastructure
└── SDLC/
    └── Product lifecycle, requirements, decisions, handovers and project governance
```

## Folder ownership rule

Each top-level directory has one responsibility.

- Android code goes in `android/`.
- Backend code goes in `backend/`.
- Tunnel/relay code goes in `relay/`.
- Shared contracts go in `shared/`.
- General documentation goes in `docs/`.
- Infrastructure/deployment files go in `infra/`.
- Cross-system tests go in `tests/`.
- Lifecycle/governance documents go in `SDLC/`.

Do not put files in the repository root unless they are genuinely repository-wide configuration or standard project entry files.

## Subfolder rule

If a new category appears, create an appropriate folder or subfolder instead of placing unrelated files beside existing files.

Example:

```text
backend/
├── auth/
├── users/
├── friends/
├── sessions/
├── signaling/
├── usage/
└── billing/
```

If a new backend category called notifications is introduced:

```text
backend/notifications/
```

Do NOT create:

```text
backend/notifications.js
backend/new_notifications/
backend/notificationStuff/
```

unless the architecture explicitly requires that structure.

## Depth rule

Use hierarchy to communicate ownership.

Preferred:

```text
android/core/network/
android/features/friends/
backend/services/signaling/
SDLC/decisions/
```

Avoid giant directories containing unrelated files.

However, do not create meaningless folders only to increase nesting. A folder must represent a real architectural or organizational category.

## Naming standard

Use predictable names.

- Directories: lowercase, hyphen-separated where needed
- Markdown documents: descriptive lowercase names with hyphens
- Source files: follow the language/framework convention
- Avoid names such as `misc`, `stuff`, `new`, `final`, `temp`, `random`, or `other` for permanent content

## Documentation placement

### Product/lifecycle documentation
`SDLC/`

### Technical reference
`docs/`

### API contracts
`shared/` when machine-readable/shared; `docs/` for explanatory documentation

### Deployment documentation
`infra/` for operational configuration; `docs/` for explanation

## SDLC organization

The SDLC folder must remain ordered and phase-based:

```text
SDLC/
├── README.md
├── AI-HANDOVER.md
├── 01-product-discovery.md
├── 02-requirements-engineering.md
├── ...
├── 25-scale-and-global-expansion.md
├── decisions/
├── requirements/
├── research/
├── architecture/
├── security/
├── testing/
├── business/
└── handovers/
```

Phase-specific material should be stored under its appropriate phase/category rather than scattered throughout the repository.

## New category procedure

Before creating a new top-level directory:

1. Check whether an existing directory already owns the content.
2. If yes, create an appropriate subfolder there.
3. If no, determine whether the category is important enough to justify a new top-level directory.
4. Document the new ownership rule in this file.
5. Update the handover/project constitution if the new category changes architecture.

## AI navigation rule

Any AI entering the repository should be able to understand the project from:

1. `SDLC/AI-HANDOVER.md`
2. `SDLC/README.md`
3. This organization standard
4. The current SDLC phase
5. The relevant source directory

An AI must inspect the existing structure before creating files.

## Anti-scattering rule

Never solve uncertainty by creating files in random locations.

If the correct location is unclear:

- inspect the architecture,
- identify the owning component,
- create a subfolder if needed,
- document the category if it is new.

Do not leave temporary prototypes, generated artifacts, logs, secrets, screenshots, downloads, or unrelated notes mixed into source directories.

## Temporary files

Temporary files must not become permanent repository content.

Use ignored local directories/files where appropriate, such as build outputs, IDE metadata, caches, local secrets, and generated artifacts.

## Generated artifacts

Generated output belongs in a clearly designated location or should remain ignored. Never mix generated build artifacts with source code.

## Secrets

Secrets never belong in the repository. Use environment configuration and secret-management systems.

## Root cleanliness

The repository root should remain minimal. A clean root is a feature, not an inconvenience.

## Change-control requirement

Changing this organization standard requires a documented reason and explicit project-owner approval because repository structure is part of the project's long-term maintainability and AI handover strategy.

## Definition of organized

The repository is considered organized when a new contributor can answer these questions without asking someone:

- Where is Android code?
- Where is backend code?
- Where is tunnel/relay code?
- Where are shared contracts?
- Where are tests?
- Where is deployment infrastructure?
- Where is documentation?
- Where is the SDLC?
- Where are architectural decisions?
- Where is the current handover state?

If those answers are obvious from the tree, the repository is organized correctly.
