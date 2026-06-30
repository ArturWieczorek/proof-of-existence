# Chapter 06 - NFT Certificate (optional)

> Goal: issue a portable, wallet-visible **certificate** for a notarization, using the standard NFT
> metadata format (CIP-25). We build the certificate metadata here; minting the token is an
> integration step.

## 1. Why a certificate

A proof lives in a transaction's metadata - useful, but not something a user "holds". An NFT
**certificate** is: mint a one-off token whose metadata embeds the proof, and send it to the
document owner. Now the owner has a token in their wallet that any explorer renders as
"Proof of Existence Certificate" with the hash and timestamp - a shareable artifact.

It is a convenience layer. The trust still comes from the hash + block time (Chapter 04); the
certificate just packages a proof nicely.

## 2. CIP-25, the NFT metadata standard

Wallets and explorers read NFT metadata in a fixed shape under label **721**:
```
721 -> { <policyId> -> { <assetName> -> { name, ...custom fields } } }
```
`Certificate.cip25(policyId, assetName, proof)` builds exactly that, putting our proof fields
(`proofHash`, `alg`, `ts`, optional `doc`) alongside the required `name`. Following the standard is
what makes the certificate show up correctly everywhere.

## 3. What we build / test
- `Certificate.cip25(...)` - the CIP-25 metadata for a certificate token.
- Test: the serialized metadata embeds the proof hash, the certificate name, the asset name, and the
  document label - an encoding-agnostic check that the structure carries the proof.

## 4. Minting (integration, described)
To actually issue the certificate you also need a **minting policy** for the token. The simplest is a
**native script** (e.g. "signed by the notary key", optionally time-locked) - no Plutus required.
The mint transaction: mint 1 token under that policy, attach this CIP-25 metadata, send the token to
the recipient, sign, submit. That is routine bloxbean minting (like a faucet), left as an integration
exercise so the chapter's unit tests stay offline.

## 5. What to notice
- **CIP-25 vs CIP-68:** CIP-25 (metadata-based) is the simplest and is fine here; CIP-68 (datum-based)
  is the modern alternative for programmable NFTs - mention it, do not need it.
- The asset name and policy id together identify the certificate; the policy id comes from your
  minting policy.
- A certificate does not add trust; it adds portability. Do not let it imply more than the proof does.

## 6. Build and commit
```bash
./gradlew spotlessApply test
git add -A && git commit -m "feat(ch06): optional CIP-25 NFT certificate metadata"
git tag ch06
```

## 7. What is next
Chapter 07 turns the library into a usable **CLI**, points it at a public testnet, and wraps up with
mainnet notes and an honest list of what we simplified.

## Glossary (Chapter 06)
- **CIP-25** - the standard on-chain NFT metadata format (label 721).
- **Certificate token** - a one-off NFT whose metadata embeds a proof.
- **Native script** - a simple (non-Plutus) policy (signatures, time locks) for minting.
- **CIP-68** - a newer datum-based NFT standard (mentioned, not used here).
