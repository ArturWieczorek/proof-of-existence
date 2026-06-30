# Chapter 00 - Orientation

> Goal: understand what we are building and why, before any code. No tools needed - just read.

## 1. What is "Proof of Existence"?

Imagine you write something today - a contract draft, a song, a research idea - and you want to be
able to prove **later** that it existed **now**, unchanged. A notary does this for paper: they stamp
your document with a date. We are building the digital version on Cardano.

The trick: you do **not** put the document on-chain. You put its **fingerprint** (a hash) on-chain,
with a timestamp. Later, anyone with the original file can re-compute the fingerprint and check it
matches the on-chain record - proving the file is identical to the one you notarized, and that it
existed at least as early as that record.

*Analogy:* it is like sealing your document in a tamper-evident envelope and having the post office
stamp the date on the **outside**. The post office never sees the contents (privacy), but the stamp
proves "this exact sealed thing existed on this date." The hash is the seal; the blockchain is the
dated stamp that nobody can backdate.

## 2. Why this is a great first project

- **Genuinely useful, and value-agnostic.** Timestamps and existence proofs do not need real money,
  so a testnet version is a real tool, not just a demo.
- **No smart contract required for the core.** Hash + write + verify is pure off-chain work. That is
  why it is the gentlest project in the portfolio. (We add an optional on-chain registry later.)
- **Privacy by design.** Only the hash is published; the file stays with you.

## 3. What you will build
- Chapter 01: a **fingerprint** - hash any file or bytes (SHA-256).
- Chapter 02: **record** a fingerprint + timestamp on-chain (transaction metadata).
- Chapter 03: **verify** - re-hash a file and confirm it matches a recorded proof.
- Chapters 04-07: what the timestamp really proves; an optional on-chain registry (Aiken) that
  prevents notarizing the same hash twice; an optional NFT certificate; and going to a public testnet.

## 4. How this course works (same as the xUSDC bridge)
- One **chapter = one git commit + tag** (`ch00`, `ch01`, ...). Check out any tag to land at that stage.
- Every chapter is a README like this: concept (+ analogy) -> what we build -> tests we write FIRST
  -> steps -> what to notice -> glossary.
- **TDD**: write a failing test, then the minimal code to pass, then tidy up. Keep it green
  (`./gradlew spotlessApply test`) before each commit.
- Continuity lives in `CLAUDE.md` (the stable plan) and `PROGRESS.md` (the living status); we update
  `PROGRESS.md` every session.
- Plain ASCII everywhere (the machine's git hooks enforce it), and no Co-Authored-By trailer.

> Note on numbers: a count like "5 tests" is true at that chapter's tag; running the tests at the
> latest code will show more as later chapters add to them. That is expected.

## 5. What is next
Chapter 01 builds the fingerprint: hashing, with tests, in pure Java - no blockchain yet.

## Glossary (Chapter 00)
- **Hash / fingerprint** - a short, fixed-size value computed from data; the same input always gives
  the same hash, and any change to the input changes it.
- **Proof of existence** - using an on-chain hash + timestamp to prove a document existed and is
  unchanged, without revealing the document.
- **Notary** - here, our tool that records and verifies these proofs.
- **Timestamp** - the recorded time a proof was put on-chain.
