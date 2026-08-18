# Phase 5 — Linko Tunnel Engine

## Objective
Implement the secure data-plane mechanism that transports authorized Receiver traffic through the Provider's connection.

## Design

The Receiver uses Android's VPN mechanism to capture traffic. The tunnel transports encrypted packets toward the Provider, directly when possible or through a Linko relay when necessary. The Provider forwards authorized traffic through its available network and returns responses through the tunnel.

## Rules

- Use a mature, audited tunneling/cryptographic protocol where practical.
- Do not invent cryptographic primitives.
- Separate authentication from transport encryption.
- Use short-lived session credentials.
- Support clean teardown.
- Define MTU, DNS, routing, IPv4/IPv6, and error behavior explicitly.

## Required tests

- Basic TCP traffic
- DNS resolution
- HTTPS traffic
- IPv4
- IPv6 where supported
- Large transfers
- Packet loss
- Latency
- Network switching
- Session interruption/recovery

## Exit criteria

A controlled test can establish an encrypted tunnel between two authorized test devices and safely route a defined test traffic set through the Provider.
