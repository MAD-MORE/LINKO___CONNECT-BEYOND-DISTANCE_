# Phase 16 — Real-World Testing

## Objective
Validate Linko against real devices, carriers, locations, network conditions, and Android behavior.

## Test matrix

- Phone-to-phone on Wi-Fi
- Mobile-to-Wi-Fi
- Mobile-to-mobile
- Same city
- Different regions
- Different countries
- Weak 2G/3G/4G/5G conditions where available
- Network switching
- Background/foreground transitions
- Battery-saving modes
- Carrier/NAT differences

## Geographic rollout for testing

1. Controlled local tests
2. Ghana regional tests
3. Selected international tests
4. Larger beta

## Metrics

- Connection success rate
- Connection establishment time
- Latency
- Throughput
- Disconnect rate
- Recovery time
- Battery impact
- Relay usage

## Exit criteria

Supported network/device matrix is documented, major carrier-specific failures have workarounds or exclusions, and reliability meets the beta target.
