# GTor research: free distributed network and VPN capability benchmark

Date: 2026-10-28
Branch: `dangerous-experiment`
Status: research only; no new IP network implemented.

## Executive summary

GTor should first strengthen its existing Tor-based VPN path. The future distributed IP network is a separate research track and must not be silently inserted into Tor circuits. “Free for users” is a product goal, not a claim that bandwidth, hosting, abuse handling, or security operations have no cost.

## Primary-source findings

### Tor relay operations and user safety

The Tor Project's current relay-operator expectations say operators should not inspect or modify traffic, should not reveal user/destination IP addresses or connection timing/volume, and should keep safe logging enabled by default. These principles should inform GTor's own diagnostics and any future participant network.

Source: https://community.torproject.org/policies/relays/expectations-for-relay-operators/

### Capacity is not just IP count

Tor's published relay requirements describe practical capacity constraints: bandwidth, concurrent connections, monthly traffic, memory, uptime, and relay type. This reinforces that an IP count alone is not a meaningful capacity or quality metric.

Source: https://community.torproject.org/relay/relays-requirements/

### Bandwidth limits and predictable resource use

Tor supports bandwidth shaping and accounting controls. GTor should prefer explicit resource limits, backoff, and measurement over claims of unlimited speed or capacity. Any tuning must be tested on representative devices and must not weaken traffic protection.

Sources:
- https://community.torproject.org/relay/setup/post-install/
- https://support.torproject.org/relays/performance/bandwidth-shaping/

### Exit relay and abuse considerations

Tor's guidance recommends that exit relays be operated on dedicated infrastructure and highlights exit-policy, DNS, hosting-provider, and abuse-response responsibilities. A future residential IP network would have its own distinct legal and abuse-response requirements; it should not be treated as a simple extension of the existing Tor relay feature.

Sources:
- https://community.torproject.org/relay/setup/exit/
- https://community.torproject.org/relay/community-resources/tor-exit-guidelines/

## Paid VPN capability benchmark for GTor

These are benchmark categories, not an instruction to add every feature. Each candidate must be evaluated against GTor's architecture and security model.

| Capability | User benefit | GTor evaluation requirement |
|---|---|---|
| Kill switch / fail-closed behavior | Reduces exposure if the tunnel fails | Must reflect real Android VPN/lockdown state and be tested under process death, reconnect, and network churn. Never claim OS lockdown if it was not verified. |
| DNS and IPv6 leak protection | Prevents traffic from bypassing intended tunnel policy | Requires independent packet-path tests; internal booleans alone are not proof. |
| Auto-reconnect | Recovers after network changes | Must invalidate old verification evidence and bind new evidence to the current session generation. |
| Split tunneling | Lets users choose which apps use the VPN | Must be explicit, testable, and never described as protecting excluded apps. Preserve current UX unless the user authorizes changes. |
| Multi-hop | Changes trust and routing assumptions | Do not add by default; requires a threat-model review and measured latency/availability trade-offs. |
| Performance metrics | Helps users diagnose slow connections | Prefer local-only, coarse metrics with transparent units; do not persist destinations, DNS queries, or per-user circuit details. |
| Independent audits and transparent claims | Improves trust | Claims must link to verifiable tests/reports and clearly state limits; no unsupported “no logs,” “unlimited,” or “leak-proof” promises. |

## Future distributed IP network: constraints before design

1. Explicit opt-in, plain-language disclosure, participant control, and immediate revocation.
2. No silent bandwidth resale, covert enrollment, or sale of user data.
3. Secure node enrollment, authentication, updates, isolation, rate limits, revocation, and abuse handling.
4. Separate trust domain from Tor; no automatic proxy chaining into Tor circuits.
5. Measure distinct active IPs, independent networks/ASNs, location coverage, uptime, and usable bandwidth separately. Never invent headline counts.
6. Define a credible funding and operating model before promising free access at scale.
7. Do not start production traffic until legal/provider terms, participant safety, privacy, and threat-model reviews pass.

## Implementation priority

1. Fix and test concrete defects in the existing codebase.
2. Establish a clean, reproducible build and lifecycle regression coverage.
3. Implement independent leak verification and controlled tests before making protection claims.
4. Measure performance before tuning.
5. Continue research on the separate IP network without implementing it yet.

## Evidence and limitations

This document records research from official Tor Project sources. It is not legal advice, does not certify GTor, and does not prove any feature passes tests. No new IP network has been created by this document.
