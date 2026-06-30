# Chapter 03 - Verify a Proof

> Goal: close the loop. Given a document you hold and a proof that was recorded on-chain, prove the
> document is the notarized one by re-hashing it and comparing.

## 1. What "verify" means

A proof is only useful if anyone can check it. Verification is simple and entirely local to the
checker:
1. Read the recorded proof (its hash, algorithm, timestamp) - in production, fetched from the chain.
2. Re-hash the document you hold, with the same algorithm.
3. If the fingerprints match, the document is byte-for-byte the one that was notarized - and the
   proof's timestamp says when.

No secret, no trust in us: the math does the work. If even one byte differs, the hashes differ and
verification fails.

## 2. What we build
- `Notary.parseProof(map)` - read a `ProofRecord` back out of proof metadata (the inverse of
  `proofMap`); this is what you do after fetching a transaction's metadata from the chain.
- `Verifier.verify(content, recorded)` - re-hash and compare; refuses (clearly) if the proof used a
  hash algorithm this tool does not compute.

## 3. Tests we write first (TDD)
- `verify` accepts the original document and rejects a tampered one.
- **End to end (offline):** build a proof -> build its metadata map -> `parseProof` it back ->
  `verify` the original against the parsed proof. This simulates the full write-then-read-then-verify
  loop without a chain.
- `verify` throws for an unknown algorithm (e.g. a blake2b proof), rather than silently failing.

## 4. Steps
- Add `parseProof` to `Notary` (read `h/alg/ts/name` back); write `Verifier.verify`.
- The chain *fetch* (find the transaction, pull its metadata) is the only integration step;
  `NotaryIT` records a proof on a devnet/testnet and self-skips unless `POE_NOTARY_MNEMONIC` +
  `POE_BACKEND_URL` are set.

## 5. What to notice / common mistakes
- **Always re-hash from the actual content.** Never "verify" by comparing two stored hashes someone
  handed you - recompute from the document in front of you.
- **Algorithm must match.** A proof states which hash it used; verify with the same one. We refuse
  loudly rather than guess.
- **What the proof does and does not say.** It proves the document existed *no later than* the
  record's time and is unchanged. It does NOT prove who authored it or that it did not exist earlier.
  (Chapter 04 unpacks exactly what the timestamp means.)

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch03): verify a document against a recorded proof"
git tag ch03
```

## 7. What is next
Chapter 04 examines timestamps and trust - block time vs the transaction validity range, and what a
proof's time really guarantees. Then optional chapters add an on-chain registry (Aiken) and an NFT
certificate.

## Glossary (Chapter 03)
- **Verify** - re-hash a document and compare to a recorded proof.
- **`parseProof`** - turn proof metadata back into a `ProofRecord`.
- **Tamper-evidence** - any change to the document breaks the match.
- **Algorithm agility** - recording which hash was used so verification uses the same one.
