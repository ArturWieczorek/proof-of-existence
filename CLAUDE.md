# CLAUDE.md - Proof of Existence (read this first)

> Fresh agent: read this, then `PROGRESS.md`. Build chapter by chapter, TDD, one git tag per
> chapter. House rules: `../../CLAUDE.md` (portfolio) and `../usdcx-java-course/CLAUDE.md`
> (the canonical exemplar to imitate). ASCII only; no Co-Authored-By trailer (machine git hooks).

## 1. What this is

A **notary / timestamping tool** on Cardano. You hash a document locally (the file never leaves your
machine), record the hash + a timestamp on-chain, and later prove the document existed by re-hashing
it and finding the matching record. Genuinely useful and entirely value-agnostic - it works the same
with testnet or mainnet coins, because the value is the proof, not money.

Testnet-first, mainnet-portable: the network, backend URL, and keys come from config; nothing in the
logic is testnet-specific.

## 2. Locked decisions
| # | Decision | Why |
|---|----------|-----|
| D1 | Off-chain in **Java 21** | the core (hash/record/verify) needs no smart contract |
| D2 | Fingerprint = **SHA-256** (`java.security.MessageDigest`) | universal, rock-solid known-answer tests, zero extra deps (blake2b-256 is the Cardano-native alternative; noted) |
| D3 | Record via **transaction metadata** first | simplest possible on-chain write; no contract needed |
| D4 | On-chain **registry validator is OPTIONAL** (Aiken, later) | enforce "a hash is notarized once"; the core works without it |
| D5 | **Single Gradle module** | the project is small; no multi-module/buildSrc needed (unlike xUSDC) |
| D6 | testnet-first, network = config | mainnet is a config change |

## 3. Tech stack (the complete allowed list)
- Java 21 + Gradle (wrapper, 8.10.2) + JUnit 5 + AssertJ + Spotless (google-java-format).
- bloxbean `cardano-client-lib` + `cardano-client-backend-blockfrost` + `cardano-client-backend-koios`
  (metadata, tx, and both a keyed and a keyless backend; Yaci DevKit reuses the Blockfrost backend).
- SHA-256 via the JDK (no crypto dependency for hashing).
- Aiken (only if/when we add the optional registry validator).
- Local: Yaci DevKit devnet (Blockfrost-compatible) then preprod/preview. Integration tests
  `@Tag("integration")` self-skip without a backend.

## 4. Repo layout
```
proof-of-existence/
  CLAUDE.md / PROGRESS.md
  build.gradle.kts / settings.gradle.kts / gradlew + gradle/wrapper
  src/main/java/org/poe/   # DocumentFingerprint, Notary (metadata), Verifier, ...
  src/test/java/org/poe/
  chapters/NN-title/README.md
  docs/                    # static web verify UI (GitHub Pages): index.html + poe-verify.js + vendor/
  infra/                   # devnet/testnet runbook (later)
```

## 5. Roadmap (chapters)
- 00 Orientation - what a notary is; why only the hash goes on-chain; how the course works.
- 01 The fingerprint - hash bytes/files with SHA-256 (pure Java, TDD).
- 02 Record a proof - write hash + timestamp into transaction metadata.
- 03 Verify a proof - re-hash a file and confirm it matches a recorded proof.
- 04 Timestamps and trust - block time vs validity range; what the timestamp really proves.
- 05 (optional) On-chain registry - an Aiken validator that rejects a duplicate hash. Built + tested
  (`aiken check`), but deliberately NOT wired into the off-chain flow or deployed (see note below).
- 06 (optional) NFT certificate - a CIP-25 certificate metadata builder. Built + tested, but we
  decided NOT to pursue the NFT path (mint) or deploy it (see the 2026-07-11 decision in PROGRESS.md).
- 07 Testnet + wrap-up - preprod config, mainnet notes, what we simplified (Merkle batching).
- 08 (optional) Web verify UI - a static GitHub Pages page (docs/): hash the file in-browser (Web
  Crypto), keyless MATCH/NO-MATCH vs the hash carried in a proof link/QR + explorer link, with an
  optional user-supplied Blockfrost key for live on-chain read. (Koios is CORS-blocked in browsers.)
- 09 CLI submit - `notary submit` records a proof on-chain: providers Koios (keyless default) /
  Blockfrost / Yaci DevKit (local); keys via cardano-cli `.sk` or a mnemonic; flags > env > defaults;
  one record path via bloxbean BackendService + TxSigner. Verified live on preprod.
- 10 Publish - make the site public on GitHub Pages, release a runnable `notary.jar`, and document
  the open metadata format + CLI usage on the page so anyone can create/read a proof with any tool.

> "(optional)" means beyond the core notary, NOT unbuilt: every chapter above is built and tested.
> Chapters 05 and 06 are intentionally left as EXTENSION POINTS - the code exists and its tests pass,
> but the live off-chain wiring / deployment is not done (05: build the insert tx; 06: mint the NFT),
> because the core notary does not need them. Anyone who forks the repo can build on them; we chose
> not to deploy them (06 by explicit decision). This is by design, and flagged for honesty.

## 6. Going to mainnet
Switch the backend URL + network in config and fund a mainnet key; the hash/record/verify logic is
identical. The only real-world upgrade is batching many hashes under one Merkle root (noted in Ch 07).

## 7. How to continue right now
Read `PROGRESS.md` -> find Current chapter + Next steps -> work TDD -> keep it green
(`./gradlew spotlessApply test`) -> update `PROGRESS.md` -> commit + tag the chapter.
