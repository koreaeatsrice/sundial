# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/) and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.1.2] - 2026-11-04

### Bug Fixes
- **asm**: dilute the day/moon clock on the client, not the world-age clock (#3) ([`0a4e39f`](https://github.com/koreaeatsrice/sundial/commit/0a4e39f9e0fca088c47302a82ad0ed25e16bf0f5))
- **release**: @-mention contributors and always credit the owner ([`afeedb2`](https://github.com/koreaeatsrice/sundial/commit/afeedb27a48d758b61472a05aea188d49d358504))
- **security**: remaining review findings (locks, null guard, automerge gate, wrapper checksum) ([`04f6cc9`](https://github.com/koreaeatsrice/sundial/commit/04f6cc93518518587083833ea3e7729315894bb0))
- **security**: release-workflow command injection + fail-soft event handlers (review findings) ([`51a34d9`](https://github.com/koreaeatsrice/sundial/commit/51a34d971c5ea0bde64f605788c6c5676e333dee))
- **ci,docs**: drop CodeQL (private repos); SECURITY.md + README corrections; release-tag hardening ([`637b659`](https://github.com/koreaeatsrice/sundial/commit/637b65930412a844b69cc1ea3963494a76f464df))
- **ci**: smoke test checks the real boot line; tag-driven version; SECURITY.md corrections (fixed built-in scale, multi-target transformer) ([`e90f968`](https://github.com/koreaeatsrice/sundial/commit/e90f968296d0a84d0e03c33220a32e3971dad539))

### Documentation & Wiki
- finish the SECURITY-REVIEW.md sync ([`9910fed`](https://github.com/koreaeatsrice/sundial/commit/9910fedbd86bc7c6b05d89aedad18f95658759fb))
- add SECURITY-REVIEW.md (dependabot triage + CodeQL result) ([`b14c13b`](https://github.com/koreaeatsrice/sundial/commit/b14c13b7c2e81ae434eee9e12a5215a4a3b488ca))

### Maintenance & CI
- run the release-engine unit tests on every push and PR (#2) ([`c15eb7a`](https://github.com/koreaeatsrice/sundial/commit/c15eb7a9b2d8c6ae92512f1bb118a10e9bdf49c4))
- bump the GTNH convention toolchain 2.0.20 -> 2.0.33 (adopt upstream) ([`f51212c`](https://github.com/koreaeatsrice/sundial/commit/f51212c4fb14ecbddab9bca08dfcc68d903762a7))
- **ci**: dependabot updates actions only (Gradle sandbox cannot resolve the GTNH toolchain) ([`dc77d2b`](https://github.com/koreaeatsrice/sundial/commit/dc77d2bc601c56247c223560c2a14cacb951182d))
- pin wrapper line endings in .gitattributes ([`a6b57d6`](https://github.com/koreaeatsrice/sundial/commit/a6b57d6a135f11c46e5271bcec73fc6aa9d2d4c2))

### Other Changes
- Bump gradle-wrapper from 9.3.1 to 9.8.0 in the gradle-dependencies group (#1) ([`31a9729`](https://github.com/koreaeatsrice/sundial/commit/31a97295966f25f4cce332cf4161cca4e61776f8))
- CI/CD: earplugger-grade workflows (build + time-scale smoke test, CodeQL + dependency graph, conventional-commits releases, dependabot), AI disclaimer + SECURITY.md ([`0013fbf`](https://github.com/koreaeatsrice/sundial/commit/0013fbf9daa64c5d05b0af40cc66106ef457e089))

### Contributors

- @uriel-runner[bot]
- @dependabot[bot]
- @koreaeatsrice

**Full changelog**: https://github.com/koreaeatsrice/sundial/compare/v1.1.1...v1.1.2
