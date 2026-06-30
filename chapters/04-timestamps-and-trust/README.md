# Chapter 04 - Timestamps and Trust

> Goal: understand exactly what a proof's timestamp guarantees - and what it does not. This is a
> thinking chapter (no new code), but it is the difference between a real notary and a fake one.

## 1. Two different "times"

Our `ProofRecord` carries a `ts` field (an ISO timestamp) in the metadata. Be honest about what it
is: **a claim written by whoever made the proof.** Anyone can put any string there. It is a
convenient hint, but it is NOT trustworthy on its own - a liar could write a future or past date.

The **trustworthy** time is the one the ledger gives you: the **block time** of the transaction that
carries the proof. You do not get it from the metadata - you read it from the chain (the block the
transaction was included in). The whole network agreed on that time; nobody can backdate it.

So when verifying for real:
- re-hash the document and find the matching proof (Chapter 03), and
- read the **block time** of that transaction as the authoritative timestamp.
Treat the metadata `ts` as a label, not as evidence.

## 2. Bounding time with the validity interval (optional, stronger)

A Cardano transaction can declare a **validity interval** in slots: `invalid-before` and
`invalid-hereafter`. The ledger refuses to include the transaction outside that window. Setting
`invalid-hereafter` to a near slot gives a ledger-enforced "this proof was recorded no later than
slot X" - a guarantee that does not depend on trusting anyone's clock. (We can add this to
`Notary.record` when running on a network; it is an integration concern, not a unit of pure logic.)

Slots are Cardano's unit of time. Converting a slot to a wall-clock time needs the network's genesis
parameters; in practice you read the block's timestamp from an indexer rather than computing it.

## 3. What a proof actually proves (and does not)

- **Proves:** this exact document (it reproduces the hash) existed **no later than** the block time.
  Change one byte and the hash no longer matches.
- **Does NOT prove:** who authored it; that it did not exist *earlier*; or anything about its
  contents (only the hash is public).

That upper-bound-on-existence is precisely what a notary stamp gives you on paper, and it is enough
for the real use cases: "I had this design / contract / idea by this date."

## 4. Why this matters for the design
It is why the security lives in the **hash + block time**, not in the metadata we wrote. The metadata
is convenience; the trust is the chain. (Chapter 05, optionally, adds an on-chain registry that
enforces an extra rule the metadata-only version cannot: a given hash can be notarized at most once.)

## 5. What is next
Chapter 05 (optional) builds a small on-chain **registry** in Aiken so a hash can be recorded only
once - the first smart contract in this project. Chapter 06 adds an NFT certificate; Chapter 07 wraps
it into a CLI and takes it to a public testnet.

## Glossary (Chapter 04)
- **Claimed time** - the `ts` someone wrote in the metadata; untrusted.
- **Block time** - the time of the block that included the transaction; trustworthy.
- **Validity interval** - `invalid-before` / `invalid-hereafter` slots that bound when a transaction
  may be included; a ledger-enforced time bound.
- **Slot** - Cardano's unit of time; converted to wall-clock via network genesis parameters.
