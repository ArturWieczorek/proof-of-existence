# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: started. Build scaffold + Ch 00 (orientation) + Ch 01 (fingerprint) in progress this session.
- Current chapter: see board below.
- Last updated: 2026-06-30
- Environment: Java 21 + Gradle wrapper 8.10.2 (reused from the xUSDC course). bloxbean 0.7.2.

### Next steps (in order)
1. Ch 02 - record a proof via transaction metadata (build the metadata; unit-test its shape).
2. Ch 03 - verify a proof (re-hash + compare to a recorded proof).
3. Ch 04 timestamps; Ch 05 optional Aiken registry; Ch 06 optional NFT cert; Ch 07 testnet + wrap-up.

## Chapter status board
Legend: [ ] not started - [~] in progress - [x] done - [blocked] blocked

| Ch | Title | Status | Tag | Notes |
|----|-------|--------|-----|-------|
| 00 | Orientation | [ ] | - | |
| 01 | The fingerprint (SHA-256) | [ ] | - | |
| 02 | Record a proof (metadata) | [ ] | - | |
| 03 | Verify a proof | [ ] | - | |
| 04 | Timestamps and trust | [ ] | - | |
| 05 | On-chain registry (Aiken, optional) | [ ] | - | |
| 06 | NFT certificate (optional) | [ ] | - | |
| 07 | Testnet + wrap-up | [ ] | - | |

## Pinned tool versions
| Tool | Version |
|------|---------|
| Java | 21 |
| Gradle (wrapper) | 8.10.2 |
| bloxbean cardano-client-lib | 0.7.2 |
| JUnit / AssertJ | 5.11.3 / 3.26.3 |
| Spotless / google-java-format | 6.25.0 / 1.22.0 |

## Decisions and deviations (append-only)
- 2026-06-30 - Single Gradle module (project is small). Fingerprint = SHA-256 via JDK (no crypto dep; blake2b-256 noted as the Cardano-native alternative). Metadata-first recording (no contract for the core).

## Session log
### 2026-06-30 - kickoff
- Did: scaffolded the repo (Gradle wrapper reused from xUSDC), wrote CLAUDE.md + PROGRESS.md, started Ch 00/01.
- Next: finish Ch 00/01, then Ch 02/03.
