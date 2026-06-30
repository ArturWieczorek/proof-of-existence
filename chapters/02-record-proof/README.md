# Chapter 02 - Record a Proof (transaction metadata)

> Goal: put a fingerprint + timestamp on-chain - the simplest way, with no smart contract - by
> attaching it to a transaction as metadata.

## 1. The simplest on-chain write: metadata

Every Cardano transaction can carry **metadata**: arbitrary structured data under a numeric
**label**. That is all a basic notary needs - we do not need a smart contract to *store* a proof, we
just attach it to a normal transaction. The transaction's place in the ledger gives it a time and
makes it permanent.

*Analogy:* metadata is the memo line on a cheque. The cheque (transaction) does its normal thing;
the memo records a note that travels with it forever.

Our proof metadata, under a label we choose (`1718`), is a small map:
```
{ h: <sha256 hex>, alg: "SHA-256", ts: "2026-06-30T12:00:00Z", name: "my-contract" }
```
Only the hash goes on-chain - the document stays private (Chapter 00).

## 2. What we build
- `ProofRecord` - the proof data, with validation (valid hash, name within limits).
- `Notary` - builds the metadata (`proofMap`, `buildMetadata`) and, for a live chain, `record(...)`
  which submits a tiny self-payment carrying the metadata.

## 3. Tests we write first (TDD)
- `ProofRecord.forContent` hashes the content and stores the algorithm + timestamp; bad hashes and
  over-long names are rejected; a null name becomes empty.
- `Notary.proofMap` carries h/alg/ts/name; the serialized metadata embeds the proof (we check the
  hash and "SHA-256" appear verbatim in the CBOR bytes - an encoding-agnostic check).

## 4. Steps
- Write the tests, then `ProofRecord` (a validating `record`) and `Notary` (metadata builders).
- The on-chain `Notary.record(...)` is written but exercised only as an integration test against a
  devnet/testnet (Chapter 03 / the runbook), so unit tests stay fast and offline.

## 5. What to notice / common mistakes
- **Metadata string limit: 64 bytes.** Each text value in Cardano metadata is capped at 64 bytes.
  A SHA-256 hex is exactly 64 chars (fine), and we cap `name` at 64 bytes. Longer values must be
  split into a list of chunks - a real constraint worth knowing.
- **Pick and document your label.** There is no global registry for proof-of-existence labels;
  `1718` is ours. Reading proofs back later means querying that label.
- **Metadata is not validated by the chain.** Anyone can write any metadata; the *trust* comes from
  the hash (only the real document reproduces it) plus the timestamp, not from the chain checking it.
  (Chapter 05 adds an optional on-chain registry that *does* enforce a rule: no duplicate hashes.)

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch02): record a proof as transaction metadata"
git tag ch02
```

## 7. What is next
Chapter 03 closes the loop: **verify** - re-hash a file and confirm it matches a recorded proof.

## Glossary (Chapter 02)
- **Transaction metadata** - structured data attached to a transaction under a numeric label.
- **Label** - the number that namespaces a piece of metadata (ours is 1718).
- **64-byte limit** - the maximum size of a single metadata text value.
- **`ProofRecord` / `Notary`** - the proof data / the component that builds and submits its metadata.
