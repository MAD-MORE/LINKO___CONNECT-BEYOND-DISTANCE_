# Phase 17 — Performance Engineering

## Objective
Make Linko fast, reliable, battery-aware, and economically sustainable.

## Initial targets

- Connection establishment target: under 5 seconds in healthy supported conditions.
- Low protocol overhead.
- Graceful recovery after temporary network loss.
- Reasonable Provider battery consumption.
- High successful-session establishment rate.

Targets are measured and revised using real data rather than assumed universally across carriers.

## Optimization areas

- Tunnel overhead
- Packet processing
- Connection setup
- Relay routing
- Backend latency
- Database queries
- Android wakeups
- Battery usage
- Memory usage

## Capacity planning

Model:

`users × concurrent sessions × average bandwidth × relay percentage × peak factor`

## Exit criteria

Performance benchmarks exist, bottlenecks are measured, critical paths meet agreed targets, and infrastructure cost under expected usage is understood.
