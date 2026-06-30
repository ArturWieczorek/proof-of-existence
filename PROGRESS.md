# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: PROJECT COMPLETE (Ch 00-07, 8 tags). Notary core (hash/record/verify) + CLI + optional
  Aiken registry + optional CIP-25 certificate. 17 Java unit tests + 7 Aiken tests green.
- Current chapter: none - done. Optional follow-ups below.
- Last updated: 2026-06-30
- Environment: Java 21 + Gradle wrapper 8.10.2. bloxbean 0.7.2. Aiken 1.1.15 (onchain/).

### Optional follow-ups
- Wire the on-chain registry insert + certificate mint off-chain (build the transactions in Java).
- Add Merkle batching (one tx for many proofs).
- Run on preprod: set POE_BACKEND_URL + POE_NOTARY_MNEMONIC, run `./gradlew integrationTest`.

## Chapter status board
Legend: [ ] not started - [~] in progress - [x] done - [blocked] blocked

| Ch | Title | Status | Tag | Notes |
|----|-------|--------|-----|-------|
| 00 | Orientation | [x] | ch00 | scaffold (single-module Gradle) + orientation |
| 01 | The fingerprint (SHA-256) | [x] | ch01 | DocumentFingerprint; KAT-verified; 4 tests |
| 02 | Record a proof (metadata) | [x] | ch02 | ProofRecord + Notary (label 1718); 6 tests |
| 03 | Verify a proof | [x] | ch03 | parseProof + Verifier; end-to-end offline; NotaryIT self-skips; 3 tests |
| 04 | Timestamps and trust | [x] | ch04 | concept chapter: claimed time vs block time, validity interval |
| 05 | On-chain registry (Aiken, optional) | [x] | ch05 | proof_registry sorted-set; uniqueness from strict sorting; 7 aiken tests |
| 06 | NFT certificate (optional) | [x] | ch06 | Certificate CIP-25 (721) metadata builder; 1 test |
| 07 | Testnet + wrap-up | [x] | ch07 | NotaryCli (hash/verify/proof) + testnet/mainnet notes + simplifications; CLI run live |

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
### 2026-06-30 - kickoff (Ch 00-03: the notary core)
- Did: scaffolded the repo (single-module Gradle, wrapper reused from xUSDC, bloxbean 0.7.2).
  Ch00 orientation. Ch01 `DocumentFingerprint` (SHA-256 via JDK MessageDigest; KAT-verified; 4 tests).
  Ch02 `ProofRecord` (validated) + `Notary` (metadata under label 1718; record() via QuickTx; 6 tests).
  Ch03 `Notary.parseProof` + `Verifier` (re-hash + compare; refuses unknown algos); end-to-end offline
  test (build->metadata->parse->verify); `NotaryIT` (@Tag integration, self-skips on POE_NOTARY_MNEMONIC
  + POE_BACKEND_URL). 13 unit tests green; spotless clean.
- Tagged: ch00, ch01, ch02, ch03.
- Verification boundary: on-chain record/fetch NOT run here (no devnet); core logic fully offline-verified.
- Next: Ch 04 timestamps; Ch 05 optional Aiken registry; Ch 06 NFT cert; Ch 07 testnet + CLI + wrap-up.
