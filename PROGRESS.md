# PROGRESS.md - Living Status Log

> Update at the end of every work session. Read `CLAUDE.md` first.

## Current state
- Status: PROJECT COMPLETE (Ch 00-09, 10 tags). Notary core (hash/record/verify) + CLI (incl. an
  on-chain `submit`) + optional Aiken registry + optional CIP-25 certificate + optional static web
  verify UI. 39 Java unit tests + 7 Aiken tests + 20 JS verify checks green. Recording proven live on
  preprod (real tx, see session log).
- Current chapter: none - done. Optional follow-ups below.
- Last updated: 2026-07-12
- Environment: Java 21 + Gradle wrapper 8.10.2. bloxbean 0.7.2 (lib + blockfrost + koios backends).
  Aiken 1.1.15 (onchain/). Web UI: static docs/ (vanilla JS ES module + vendored MIT QR); node/browser
  tested (Node 22 + Chromium).

### Optional follow-ups
- [DONE 2026-07-12, Ch08] Static web verify UI (GitHub Pages, docs/). Hash the file in-browser
  (Web Crypto SHA-256, never uploaded), MATCH / NO MATCH + explorer link + shareable QR deep-link
  (net/tx/h). IMPORTANT correction to the original plan: a keyless *live* read via Koios is NOT
  possible from a browser - Koios sends no Access-Control-Allow-Origin, so CORS blocks every call
  (verified in headless Chromium). Final design: keyless DEFAULT compares the file to the hash
  carried in the proof link/QR and confirms the record via the explorer link; OPTIONAL live read
  uses the user's own free Blockfrost project id (Blockfrost is CORS-enabled, keyed). Remaining
  boundary: a real submit+verify on public preprod (needs a funded key + Blockfrost id) - all logic
  is proven offline + in-browser; the live-key round-trip against real Blockfrost servers is the one
  step not yet run.
- Notarize FROM the web page (connect CIP-30 wallet, sign, submit metadata tx): decided OUT OF SCOPE
  for PoE - it needs a wallet and duplicates the browser-wallet-submit pattern already built in the
  memory-wall project; keeping PoE true to "no wallet/contract needed to timestamp". If ever wanted,
  add as a further optional chapter reusing memory-wall's CIP-30 flow, NOT a separate project.
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
| 08 | Web verify UI (optional) | [x] | ch08 | static docs/ page: local Web Crypto hash, keyless compare + explorer, optional Blockfrost live read, QR deep-link; 20 JS checks + Chromium-driven |
| 09 | CLI submit (record on-chain) | [x] | ch09 | notary submit: Koios(keyless default)/Blockfrost, .sk/mnemonic, flags+env; one record path via BackendService+TxSigner; verified live on preprod |

## Pinned tool versions
| Tool | Version |
|------|---------|
| Java | 21 |
| Gradle (wrapper) | 8.10.2 |
| bloxbean cardano-client-lib | 0.7.2 |
| JUnit / AssertJ | 5.11.3 / 3.26.3 |
| Spotless / google-java-format | 6.25.0 / 1.22.0 |

## Decisions and deviations (append-only)
- 2026-07-12 - Ch09: added dependency `cardano-client-backend-koios:0.7.2` (reason: a keyless provider
  so the CLI `submit` works with no API sign-up; Blockfrost still supported and stays the browser
  default; Yaci reuses the Blockfrost backend against a local URL). Added a branch-free
  `Notary.record(backend, address, TxSigner, proof)` so mnemonic and cardano-cli `.sk` keys share one
  path. CLI config = flags > env > defaults; secrets (mnemonic) env-only. Ogmios/Kupo (local node) and
  a distributable binary deferred (documented as follow-ups in Ch09). Detailed beginner walkthrough
  written as chapters/09-cli-submit/README.md.
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
### 2026-07-12 - Ch09: CLI `submit` (record on-chain), verified live on preprod
- Added `notary submit <file> [name]`: providers Koios (keyless default) / Blockfrost (keyed) / Yaci
  (local, reuses the Blockfrost backend at http://localhost:8080/api/v1/); keys via cardano-cli `.sk`
  (+ --address) or POE_NOTARY_MNEMONIC; config flags > env > defaults; one record path via bloxbean
  `BackendService` + `TxSigner` interfaces (backendFor/senderFor factories; ProofSubmitter test seam;
  resolveConfig pure). Added the `cardano-client-backend-koios` dependency.
- A unit test caught a real bug: Koios (Retrofit) requires a trailing slash on the base URL; fixed the
  defaults. 39 Java tests green (spotless clean).
- Verified LIVE on preprod with the user's cardano-cli `.sk` via keyless Koios: tx
  1d65dd63b4e091977742e7077197bee48554dd259a892f42c053234760032fcc, block 4928405, block time
  2026-07-12T17:02:05Z; on-chain label 1718 h == file hash (267219dd...); confirmed a normal ed25519
  `.sk` signs correctly through bloxbean. Verified via the web page (MATCH, keyless) and `notary
  verify` (MATCH).
- Wrote a detailed, beginner-first chapter doc (interfaces/factories/seam/pure-function explained,
  provider + key concepts, real command/output examples, the CORS asymmetry, going-to-mainnet).

### 2026-07-12 - Ch08: static web verify UI (built + verified), CORS finding
- Built docs/ (GitHub Pages): poe-verify.js (pure, DOM-free ES module), index.html, vendored MIT QR
  (docs/vendor/qrcode.js, block-glyph ASCII renderer stripped to stay ASCII-only), docs/test/verify.test.mjs.
- KEY FINDING (fact, not assumed): browsers CANNOT read Koios - it returns no
  Access-Control-Allow-Origin, so CORS blocks every call (curl showed the header missing on the
  actual response; a real headless-Chromium fetch from a clean local origin got "TypeError: Failed to
  fetch" for GET /tip, GET/POST tx_metadata, and a text/plain "simple" POST). Blockfrost, by
  contrast, returned a real HTTP 403 (no CORS error) -> it IS browser-usable, but needs a key.
- Design (user-approved): keyless DEFAULT (hash file locally, compare to the hash in the proof
  link/QR, confirm the record via the explorer link) + OPTIONAL live read with the user's own free
  Blockfrost project id (auto-reads on-chain hash + block time, cross-checks the link).
- Verified with real execution (no guessing): (1) sha256Hex == Java CLI `run --args="hash <file>"`
  byte-for-byte on a real file (5c66.../b184... across runs); (2) 20 zero-dependency node checks over
  the shipped module (compare, parse label 1718 in the real Blockfrost /metadata shape, fetch path
  via injected fetch incl. project_id header + 404, deep-link round-trip, QR encode); (3) QR encoded
  by the shipped lib and DECODED back to the exact link by an independent reader (jsQR); (4) the real
  page driven in headless Chromium: keyless MATCH + NO_MATCH, optional Blockfrost MATCH with block
  time+height, cross-check warning, QR canvas, zero console errors; (5) Koios CORS block reproduced
  in-browser. Java side unchanged and still green.
- Verification boundary: a real submit+verify on public preprod (funded key + Blockfrost id) not yet
  run; every code path is otherwise proven. Tagged ch08.
- Decided: notarize-from-web (CIP-30) stays out of scope (see follow-up note); memory-wall already
  teaches browser-wallet submit.

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
