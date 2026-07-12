# Chapter 08 - A web page anyone can verify with (optional)

> Goal: put the "verify" step from Chapter 03 on a web page a non-technical person can use. Drop in a
> file, get MATCH / NO MATCH, and a one-click jump to the on-chain record - with the file never
> leaving the browser and no wallet, no key, and no backend required.

## 1. Concept (and an analogy)

A proof is only as useful as the number of people who can check it. Our Java CLI verifies, but it
asks the checker to install a toolchain. Chapter 03 said verification is "entirely local to the
checker" - so it should run in a browser, which everyone already has.

*Analogy:* the notary's dated stamp is public, but you still need somewhere to hold the document "up
to the light" and compare the seal. This page is that public inspection station. It is just a static
web page - anyone can host a copy (we use GitHub Pages) - and it never phones home: your file is
hashed on your own device with the browser's built-in SHA-256 (Web Crypto), exactly like the CLI.

**What the page proves, and how it gets the record.** The re-hash is always local and keyless: "does
this file reproduce hash H?" The only question is where H (and the block time) come from:

- **Keyless default.** H travels inside the proof link / QR (`...#verify?net=..&tx=..&h=..`). The
  page compares your file to H with zero network calls, and shows a **view-on-explorer** link so you
  can confirm with your own eyes that H really is the hash recorded in that transaction, at that
  block time. The explorer is a trusted public witness; the durable thing the link carries is the
  transaction hash.
- **Optional live read.** Paste your own free Blockfrost project id and the page reads the on-chain
  proof + block time directly and cross-checks it against the link (warning you if they differ).

**Why not read the chain for free in the browser?** We tried. Koios is a great keyless API, but from
a browser every call fails: Koios does not send an `Access-Control-Allow-Origin` header on its
responses, so the browser's CORS policy blocks them (verified from a real headless browser - see the
verification notes). Blockfrost *does* send CORS headers but requires an API key, which cannot be
baked into a public static site without leaking it. Hence the design above: keyless by default (link
+ explorer), with an optional user-supplied Blockfrost key for automatic on-chain read.

## 2. What we build (under `docs/`, which GitHub Pages serves)
- `docs/poe-verify.js` - the pure logic, framework-free and DOM-free, so the identical file runs in
  the browser and under Node tests: `sha256Hex`, `compareHash` (keyless), `compareProof`
  (algorithm-aware, mirrors `Verifier`), `parseBlockfrostProof` (reads label 1718), `fetchProofBlockfrost`
  (optional live read), and `buildDeepLink` / `parseDeepLink` for the shareable QR link.
- `docs/index.html` - the page: network + tx + file inputs, a MATCH / NO MATCH verdict, the recorded
  proof, the explorer link, and a QR + link to share the verification.
- `docs/vendor/qrcode.js` - a vendored MIT QR encoder (kazuhikoarase, v2.0.4), trimmed of its ASCII
  block-glyph renderer (this repo is ASCII-only); we render the matrix to a canvas ourselves.

## 3. Tests we write first (and how to run them)
`node docs/test/verify.test.mjs` (no dependencies) imports the shipped files and checks:
- SHA-256 known-answer tests (the same vectors as the Java `DocumentFingerprintTest`).
- `compareHash`: MATCH, NO_MATCH, missing-expected, case-insensitive.
- `compareProof`: MATCH, NO_MATCH, and UNSUPPORTED_ALG for a non-SHA-256 proof (refuse, do not guess).
- `parseBlockfrostProof`: finds label 1718 in the real Blockfrost `/metadata` shape; rejects a bad hash.
- `fetchProofBlockfrost` with an injected `fetch`: correct URLs, `project_id` header, parsing, 404.
- Deep-link round-trip and QR encoding.

Facts established during development (documented here for honesty; they need extra tools, so they are
not in the zero-dependency test): the page's `sha256Hex` is byte-identical to `./gradlew run
--args="hash <file>"` on a real file; the QR was decoded back to the exact link by an independent
reader (jsQR); the real page was driven in headless Chromium (keyless MATCH/NO_MATCH, the optional
Blockfrost path with block time, the cross-check warning, QR canvas, no console errors); and Koios
was confirmed CORS-blocked from a browser while Blockfrost was not.

## 4. Steps
1. Write `docs/poe-verify.js` as pure functions (no `document`/`window` at import time) so Node can
   test the exact browser code.
2. Build `docs/index.html`: hash the chosen file locally, then either compare to the link's hash
   (keyless) or, if a Blockfrost id is given, read the proof live and compare.
3. Vendor the QR encoder; draw the deep-link QR on a canvas.
4. Verify: `node docs/test/verify.test.mjs` (keep it green).
5. Publish: repo **Settings -> Pages -> Build and deployment -> Deploy from a branch -> `main` /
   `docs`**. The page is then at `https://<user>.github.io/<repo>/`.

## 5. What to notice / common mistakes
- **Re-hash locally, always.** A MATCH means "this file reproduces hash H." That is the whole proof
  of *integrity*. Confirming H is the hash actually on-chain (existence + time) is the explorer's job
  in the keyless path, or Blockfrost's in the optional path - never trust a hash someone handed you
  without seeing it on the ledger.
- **Verification is holder-agnostic.** It depends only on the file and the public record - never on
  "who holds a token/wallet." (This is why Proof of Existence needs no NFT; see the 2026-07-11
  decision in `PROGRESS.md`.)
- **Algorithm agility.** The proof states its algorithm; we recompute with the same one and refuse
  loudly for anything but SHA-256, rather than pretend.
- **CORS is real.** A static page can only call APIs that send CORS headers. Koios does not; browsers
  block it. This is a network-policy fact, not a bug in our code.
- **The key is read-only and never leaves the browser.** A Blockfrost project id grants reads only;
  the page never stores or forwards it. It is optional - the page verifies without it.

## 6. Build and commit
```bash
node docs/test/verify.test.mjs      # 20 checks, all green
./gradlew spotlessApply test        # Java unchanged, still green
git add -A && git commit -m "feat(ch08): static web verify UI (keyless + optional Blockfrost) + QR"
git tag ch08
```

## 7. What is next
This is the last planned chapter. A natural next step is to notarize *from* a web page (connect a
CIP-30 wallet, sign, submit the metadata tx) - but that is deliberately out of scope here: it needs a
wallet and duplicates the browser-wallet-submit pattern already taught by the Memory Wall project.
Proof of Existence stays true to its thesis - you need no wallet and no contract to timestamp a
document. See the follow-up note in `PROGRESS.md`.

## Glossary (Chapter 08)
- **Web Crypto** - the browser's built-in cryptography (`crypto.subtle`); we use its SHA-256 so the
  file is hashed locally and never uploaded.
- **CORS** - Cross-Origin Resource Sharing; the browser rule that a page may only read a response
  from another origin if that origin allows it via headers. Koios does not; Blockfrost does.
- **Deep-link** - a URL that carries the verification parameters (`net`, `tx`, `h`) in its fragment,
  so opening it (or scanning its QR) pre-fills the page.
- **Explorer** - a trusted public website that displays raw on-chain data (here, the notarize
  transaction and its metadata), used to confirm the record independent of our page.
- **Blockfrost / Koios** - hosted APIs that let a client read Cardano chain data without running a
  node. Blockfrost is CORS-enabled but keyed; Koios is keyless but not CORS-enabled.
