# Security Policy

## Supported Versions

Security updates are provided for the latest minor release line:

| Version | Status |
| ------- | ------ |
| 1.1.x   | Maintained |
| < 1.1.0 | Unsupported |

---

## Reporting a Vulnerability

If you identify a security vulnerability in `sundial` (for example anything
that could abuse the coremod transformer, the config parsing, or a crafted
world whose time handling could crash or corrupt a server), please report it
responsibly.

### How to Report
- Open a private advisory report through GitHub Security Advisories at
  https://github.com/koreaeatsrice/sundial/security/advisories/new
- Or email `koreaeatsrice@gmail.com` with the subject line
  `[SECURITY] sundial Vulnerability Report`.

### What to Include
1. Clear description of the vulnerability and potential impact.
2. Steps to reproduce or proof-of-concept (a small test pack is ideal).
3. Mod version, GTNH pack version, and the relevant `logs/fml-server-latest.log` excerpt.
4. Any suggested remediations or mitigations.

### Response Expectations
Reports are handled on a best-effort basis as time permits. There are no formal
response timelines or resolution SLAs. Confirmed security fixes will be tagged
and released via standard repository releases.

---

## Security Model & Threat Boundaries

Sundial is a **server-side time-dilation coremod** that replaces the vanilla
daylight-cycle update with a smooth, monotonic clock. Its security-relevant
boundaries are:

1. **Fixed transformation targets:** the coremod transformer rewrites a small,
   fixed set of vanilla methods (the server world tick, the client world tick,
   and the server's time-sync cadence). It targets no user-supplied class or
   method names.
2. **No network, no code loading:** the mod opens no sockets, downloads
   nothing, and executes no code from data. Client machines need nothing
   installed (`acceptableRemoteVersions = "*"`).
3. **Deterministic data model:** world time is advanced like a recurring
   `/time add` — no external input feeds the clock.
4. **Fixed built-in scale:** the dilation factor is a constant in this build
   (no config file to tamper with); changing it requires a new release.