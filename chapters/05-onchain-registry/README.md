# Chapter 05 - On-chain Registry (Aiken, optional)

> Goal: add the one guarantee a metadata-only notary cannot give - **a hash can be recorded at most
> once** - by storing notarized hashes in an on-chain set. This is the first smart contract in this
> project. Verified with `aiken check`; wiring it into the Java notary is left as an integration step.

Files: `onchain/lib/poe/registry.ak`, `onchain/validators/proof_registry.ak`.

## 1. Why metadata alone is not enough

Anyone can attach any metadata to any transaction (Chapter 02). So the metadata-only notary cannot
stop the *same* hash being notarized twice - there is no shared state checking "has this been seen?"
To enforce uniqueness you need **on-chain state**: a set of notarized hashes that the ledger updates
under rules.

## 2. A set as a sorted linked list

Cardano has no built-in set, so we build one the standard way: a **sorted linked list**. Each
notarized hash is a node `{key, next}`; nodes are chained in ascending order, bracketed by a root
sentinel (`""`) and a tail sentinel (`max_key`). This is the same trick the xUSDC bridge used for its
nonce list.

The magic is in **insertion**. To add hash `K`, you must find the unique node whose gap contains it -
`covers(node, K)` means `node.key < K < node.next`, strictly - and split it:
```
before: anchor(key=A, next=B)
after:  anchor(key=A, next=K)  ++  inserted(key=K, next=B)
```
Because `K` must be **strictly between**, a hash that is already in the set can never be inserted
again (it equals a boundary, so no gap strictly contains it). That is the uniqueness guarantee, and
it falls out of keeping the list sorted.

## 3. What we build
- `lib/poe/registry.ak` - the pure logic: `RegistryNode`, `covers`, `valid_insertion`. Unit-tested,
  including `duplicate_hash_is_rejected`.
- `validators/proof_registry.ak` - a spend validator that, on `InsertHash { key }`, requires exactly
  two continuing outputs (the updated anchor + the new node), each carrying a registry node token,
  and checks `valid_insertion` (in either output order).

## 4. Tests we write first (TDD)
- Pure: root covers any hash; `covers` is strict at boundaries; a valid split passes; a duplicate is
  rejected.
- Validator: a real insertion passes; inserting a duplicate is rejected; a node output missing its
  marker token aborts (the `fail`-style test).
Run: `cd onchain && aiken check` (7 tests pass).

## 5. What to notice / common mistakes
- **Uniqueness is a side effect of sortedness**, not a separate check - elegant and cheap.
- The validator stays thin; the real rule lives in the pure `valid_insertion` (easy to test).
- **Concurrency:** two inserts that touch the same anchor node contend (one tx invalidates the
  other's input). Real systems pre-seed many nodes to spread inserts out - noted as the production
  upgrade.
- We verify the on-chain logic here; **building the insert transaction off-chain** (find the anchor,
  produce both outputs, mint the node token) is an integration step left for when you deploy.

## 6. Build and commit
```bash
cd onchain && aiken fmt && aiken check && aiken build   # plutus.json blueprint
cd ..
git add -A && git commit -m "feat(ch05): optional on-chain proof registry (Aiken sorted-set, uniqueness)"
git tag ch05
```

## 7. What is next
Chapter 06 (optional) issues a portable **NFT certificate** per notarization. Chapter 07 wraps the
notary in a CLI and takes it to a public testnet.

## Glossary (Chapter 05)
- **On-chain set / registry** - state on the chain recording which hashes are notarized.
- **Sorted linked list** - nodes chained by key in ascending order; the building block for the set.
- **`covers` / anchor** - the unique node whose gap strictly contains a new key.
- **Insertion split** - replacing the anchor with [updated anchor] + [new node]; duplicates cannot
  split, so they are rejected.
- **Continuing outputs** - the new UTxOs a spend must produce (here, two: anchor + new node).
