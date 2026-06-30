# Chapter 01 - The Fingerprint (SHA-256)

> Goal: hash any document into a short, fixed fingerprint, test-first, in pure Java. No blockchain
> yet - this is the foundation everything else builds on.

## 1. What a hash gives us

A **hash** turns any data, however large, into a short fixed-size value (256 bits for SHA-256). Two
properties make it perfect for a notary:
- **Deterministic:** the same document always produces the same fingerprint.
- **Tamper-evident:** change even one byte and the fingerprint changes completely.

So if we publish only the fingerprint, we reveal nothing about the document, yet anyone holding the
original can prove it is the one we notarized.

*Analogy:* a hash is like a wax seal pressed from a document. The seal is tiny and gives away none of
the text, but if the text changes, the seal no longer matches.

We use **SHA-256** (from the JDK): universal, dependency-free, with published "known-answer" test
vectors we can check against. (Cardano's native hash is blake2b-256; SHA-256 is the pragmatic choice
for an off-chain notary, and nothing later depends on which we pick.)

## 2. What we build
`DocumentFingerprint` (a `record`): `of(bytes)`, `ofFile(path)`, `hex()`, and `matches(bytes)`.

## 3. Tests we write first (TDD)
Start from what must be true, then make it pass:
1. **Known-answer vectors** - `sha256("")` and `sha256("abc")` must equal their published values.
   This is the strongest test: if these match, our hashing is genuinely correct, not just
   self-consistent.
2. **Deterministic + sized** - same input -> same hex; 32 bytes / 64 hex chars.
3. **Collision avoidance** - different documents -> different fingerprints.
4. **`matches()`** - recognises the original, rejects a one-character change.

## 4. Steps
- Write `DocumentFingerprintTest` first (it will not compile - that is your "red").
- Implement `DocumentFingerprint` with `MessageDigest.getInstance("SHA-256")`, `HexFormat` for hex,
  and `Arrays.equals` for `matches`.
- `./gradlew spotlessApply test` -> green.

## 5. What to notice / common mistakes
- **Hash the bytes, not a String.** Always fix the charset (we use UTF-8) so the same text hashes the
  same on every machine. Hashing a `String` directly invites platform-default-charset bugs.
- **`matches()` re-hashes and compares** - never trust a hash someone hands you without recomputing
  from the actual content.
- A fingerprint is a one-way street: you can go document -> hash, never hash -> document. That is the
  privacy guarantee.

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch01): SHA-256 document fingerprint"
git tag ch01
```

## 7. What is next
Chapter 02 puts a fingerprint on-chain: we record the hash + a timestamp in a transaction's metadata.

## Glossary (Chapter 01)
- **SHA-256** - a 256-bit cryptographic hash function with well-known test vectors.
- **Known-answer test (KAT)** - checking your implementation against published input/output pairs.
- **Hex** - the fingerprint written as 64 hexadecimal characters.
- **`MessageDigest`** - the JDK class that computes hashes.
- **`matches()`** - re-hash some content and compare to a stored fingerprint.
