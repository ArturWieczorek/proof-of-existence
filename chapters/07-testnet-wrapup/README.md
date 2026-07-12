# Chapter 07 - A CLI, a Public Testnet, and Wrap-up

> Goal: turn the library into a usable command-line tool, point it at a public testnet, note what
> changes for mainnet, and be honest about what we simplified. This chapter completes the core
> notary; two optional chapters follow - 08 (a static web verify UI) and 09 (an on-chain `submit`
> command in the CLI).

## 1. The CLI (`NotaryCli`)

A tiny front end makes the notary a real tool:
```bash
./gradlew run --args="hash <file>"              # print the document's SHA-256 fingerprint
./gradlew run --args="verify <file> <hashHex>"  # MATCH / NO MATCH
./gradlew run --args="proof <file> [name]"      # the proof record you would record on-chain
```
The core is a pure `run(String[]) -> String`, so it is unit-tested without touching stdout (hash,
verify, and usage are all covered). For a standalone binary, `./gradlew installDist` produces a
runnable script under `build/install/`.

Recording a proof on-chain (and minting a certificate) needs a backend + key, so at this stage it
stays in `Notary.record(...)` / `Certificate` (the integration path), not in this offline CLI.
(Chapter 09 later adds a `notary submit` command that brings recording into the CLI, with a keyless
provider by default.)

## 2. Going to a public testnet (preprod/preview)

Nothing in the code changes - only configuration:
- Set `POE_BACKEND_URL` to a preprod Blockfrost endpoint (project id from blockfrost.io) or your own
  Ogmios+Kupo; set `POE_NOTARY_MNEMONIC` to a funded preprod account
  (fund it from the [preprod faucet](https://docs.cardano.org/cardano-testnets/tools/faucet)).
- Run the on-chain path: `./gradlew integrationTest` runs `NotaryIT`, which records a real proof and
  returns a transaction hash. Then look it up in a preprod explorer and see your proof in the
  metadata under label 1718.

To verify "for real" you fetch the transaction's metadata and its **block time** (Chapter 04) from
the backend, parse the proof (`Notary.parseProof`), and `Verifier.verify` your file against it.

## 3. Going to mainnet

The same: point the backend URL + network at mainnet and fund a mainnet key. The hash/record/verify
logic is identical (testnet-first, mainnet-portable by design). The one real upgrade is **Merkle
batching**: instead of one transaction per document, hash many documents into a Merkle tree and
record only the root, then prove any single document with a small Merkle path. That turns N proofs
into one transaction - the standard way production notaries keep fees flat.

## 4. What this course simplified (read before reusing)

- **Metadata-only core.** The default notary records proofs as metadata; the on-chain uniqueness
  **registry (Chapter 05) is optional** and its off-chain wiring (build the insert transaction) is
  left as integration.
- **SHA-256, not blake2b-256.** Pragmatic and universal; blake2b-256 is the Cardano-native option.
- **The metadata timestamp is a claim.** Trust the block time, not the `ts` we wrote (Chapter 04).
- **No Merkle batching** (one tx per proof) - noted above as the production upgrade.
- **Verification reads block time from an indexer**, rather than computing it from genesis params.
- **Certificate minting** (Chapter 06) builds the CIP-25 metadata; the mint itself is integration.

## 5. Security recap

- Trust comes from **the hash + the block time**, both of which nobody can forge or backdate.
- **Privacy:** only the hash is published; your document never leaves your machine.
- **Uniqueness** (if you add the registry): a hash can be notarized at most once, enforced on-chain.
- **Keys** live in env/secrets, never committed.

## 6. You did it

From "what is a hash?" to a working, testnet-ready, mainnet-portable notary - a CLI, an off-chain
library (hash/record/verify), an optional on-chain registry in Aiken, and an optional NFT
certificate, with tests at every layer and honest notes on the edges. That is the first project in
the portfolio complete in the house style.

```bash
git add -A && git commit -m "feat(ch07): CLI + testnet config + wrap-up (project complete)"
git tag ch07
```

## Glossary (Chapter 07)
- **CLI** - the command-line front end (`hash` / `verify` / `proof`).
- **Faucet** - a service that hands out free testnet funds.
- **Merkle batching** - anchoring many document hashes under one Merkle root in a single transaction.
- **Block time** - the trustworthy timestamp, read from the chain at verification.
- **testnet-first, mainnet-portable** - the same code runs on any network; only config changes.
