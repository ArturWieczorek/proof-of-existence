# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: PROJECT COMPLETE (Ch 00-07, 8 tags). Notary core (hash/record/verify) + CLI + optional
  Aiken registry + optional CIP-25 certificate. 17 Java unit tests + 7 Aiken tests green.
- Current chapter: none - done. Optional follow-ups below.
- Last updated: 2026-06-30
- Environment: Java 21 + Gradle wrapper 8.10.2. bloxbean 0.7.2. Aiken 1.1.15 (onchain/).

### Optional follow-ups
- Static web verify UI (GitHub Pages friendly; no backend). Hash the file in-browser (Web Crypto
  SHA-256, file never uploaded), read the proof back via a keyless provider (Koios) by tx hash,
  show MATCH / NO MATCH + the block time. Emit a shareable QR that encodes a verify deep-link
  carrying { txHash, h } (e.g. .../#verify?tx=<txHash>&h=<hash>); the verify page also renders a
  "view on explorer" button built from the txHash. One QR then gives both: (a) the real file
  re-hash -> MATCH check, and (b) a one-click jump to the trusted public explorer showing the raw
  on-chain record. Note: the explorer link proves the record exists; only the re-hash proves the
  file matches it. Encode the durable txHash (explorer URL is just a convenience wrapper).
- CIP-20 (label 674) human-readable tx message on the notarize tx: attach a readable one-liner
  (e.g. msg: ["Proof of Existence", "sha256: <hash>", "doc: <name> ts: <ts>"]) so the transaction
  reads nicely in wallets that render CIP-20. Keep the structured proof under label 1718 for machine
  parsing; 674 is the human view. Costs nothing extra (still a plain memo tx, no min-UTxO).
- Wire the on-chain registry insert off-chain (build the insert transaction in Java).
- NFT certificate mint (Ch06 cip25 builder -> a real mint tx): NOT advised - see the 2026-07-11
  decision below. Ch06's metadata builder stays as-is (built, not extended).
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
- 2026-07-11 - Do NOT pursue the NFT certificate path (neither notary-mint nor user self-mint). Why:
  (1) it adds portability, not proof - the proof is always the hash + block time, and transferring or
  burning the token does not lose it, so a wallet-held token is packaging, not evidence; (2) it costs
  a mint fee + ~1.2-1.5 ADA locked min-UTxO per certificate (metadata costs only a fee); (3) self-mint
  fixes the economics but reopens findability (own policy -> the policyId becomes the receipt; a shared
  open policy lets anyone mint look-alikes); (4) the "visual" appeal is delivered instead by the static
  web UI + QR + CIP-20 message - all metadata-based, no tokens - which keeps PoE's thesis clean (no
  token or contract needed to timestamp a document); (5) minting depth belongs to the nft-ticketing
  project, and per the portfolio NO project does wallet self-mint, so skipping it here leaves no gap.
  Ch06's Certificate.cip25 metadata builder remains committed but is intentionally left unextended.

## Session log
### 2026-07-11 - design discussion (no code): display + verify UX, NFT tradeoffs
- Clarified: the proof lives in transaction METADATA (label 1718), not in a datum. Datum only
  appears in the optional Ch05 registry (a {key,next} set node), and its off-chain wiring is still
  a TODO. Metadata is visible in any explorer's metadata tab.
- Concluded (design themes, not new code): metadata IS the proof (hash + block time); the index,
  JSON/QR receipt, and NFT certificate are all just ways to FIND and PACKAGE that proof, not to
  strengthen it. The NFT (Ch06) adds portability/wallet-visibility, not proof, and costs a mint fee
  plus ~1.2-1.5 ADA locked min-UTxO per cert; transferring/burning the token does NOT lose the
  proof (the mint tx + its metadata are permanent) - only "prove by showing my wallet" breaks, which
  was never the real proof. So the NFT stays optional; metadata is the sensible default.
- Verification must be holder-agnostic: re-hash the file + look up the on-chain record; never build
  verify on "do you currently hold the asset".
- Standards: no CIP-25 equivalent renders arbitrary tx metadata as a card. CIP-20 (label 674) is the
  one that wallets show nicely for a plain tx (as a readable message). Added both a static verify UI
  + QR follow-up and a CIP-20 follow-up above.
- Next: pick one of the follow-ups when returning (static verify UI + QR, or CIP-20 message, or the
  live preprod submit to get a real explorer link).

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
