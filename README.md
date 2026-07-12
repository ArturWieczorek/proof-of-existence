# Proof of Existence - a Cardano notary

Prove that a document existed, unchanged, at a point in time - without ever revealing the document.
You hash the file locally, record only the hash + a timestamp on the Cardano blockchain, and later
anyone can re-hash the file and confirm it matches the on-chain record. The blockchain supplies a
trustworthy, un-backdatable timestamp; the file never leaves your machine.

- **Verify a proof (no install, no wallet, no key):** https://arturwieczorek.github.io/proof-of-existence/
- **Download the CLI (`notary.jar`, needs Java 21):** https://github.com/ArturWieczorek/proof-of-existence/releases/latest/download/notary.jar
- **Releases:** https://github.com/ArturWieczorek/proof-of-existence/releases

This is also a step-by-step, test-driven **course**: each chapter under `chapters/NN-*/README.md`
builds one piece, and each is one git commit + tag (`ch00` ... `ch10`). Start at
`chapters/00-orientation/README.md`.

---

## What it is (in one picture)

```
  YOUR MACHINE                                   CARDANO  (devnet / preprod / mainnet)
  +---------------------+                        +-----------------------------------+
  |  document           |   ---- SHA-256 --->    |  transaction metadata, label 1718 |
  |  (stays private)    |     (only the hash)    |  { h, alg, ts, name }             |
  +---------------------+                        +-----------------------------------+
        |                                              |          ^
        | fingerprint (64 hex)                         |          | block time
        |                                              |          | (the trustworthy timestamp)
        +--- record:  notary submit  -----------------+          |
        |                                                         |
        +--- verify:  re-hash the file + compare  <---------------+
             (web page or CLI - no key, no wallet, holder-agnostic)
```

- **Only the hash goes on-chain.** Your document stays with you (privacy).
- **The proof is the hash + the block time** on a public ledger. Everything else - this tool, the
  website, your key - is just machinery to *write* and *find* that proof.
- **Verifying needs nothing** but the file and the public record. It never depends on holding a token
  or a key.

## The open format (label 1718)

A proof is a normal Cardano transaction carrying this note in its metadata under label **1718**:

```json
{ "1718": { "h": "<document SHA-256, 64 hex>", "alg": "SHA-256", "ts": "<ISO-8601>", "name": "<label>" } }
```

That is the entire contract. Any tool that can attach this note creates a proof; any tool can read it
back. The CLI and the website are conveniences, not a lock-in. (Each metadata text value is capped at
64 bytes; a SHA-256 hex is exactly 64.)

## Try it right now

The site is pre-loaded with a real preprod proof. Open this link, then drop in a file containing
exactly the two lines below:

- Link: https://arturwieczorek.github.io/proof-of-existence/#verify?net=preprod&tx=1d65dd63b4e091977742e7077197bee48554dd259a892f42c053234760032fcc&h=267219dda604092c126441daa8234ddad19882699ed0b8e889a449c32fd1dcd4
- File contents (exact bytes):
  ```
  Proof of Existence - real preprod notarization
  Created for the ch08 live end-to-end test on 2026-07-12.
  ```

You will see **MATCH**, plus a "view the raw on-chain record" link to the transaction.

---

## TL;DR - the CLI (`notary.jar`)

Needs **Java 21**. Download `notary.jar` (link above), then:

```bash
# fully local, no key, no network, no account:
java -jar notary.jar hash   myfile.pdf                 # print the SHA-256 fingerprint
java -jar notary.jar verify myfile.pdf <hash>          # MATCH / NO MATCH
java -jar notary.jar proof  myfile.pdf "my label"      # print the proof record you would record

# record a proof on-chain (the only command that needs a key + network):

# --- preprod (a public testnet), keyless Koios (the default backend) ---
java -jar notary.jar submit myfile.pdf "my label" \
    --network preprod \
    --signing-key payment.skey --address addr_test1v...your-address

# --- mainnet, via Blockfrost (needs a free mainnet project id from blockfrost.io) ---
export POE_NOTARY_MNEMONIC="word word word ... word"   # a secret: env, never a flag
java -jar notary.jar submit myfile.pdf "my label" \
    --network mainnet \
    --backend blockfrost --blockfrost-project-id mainnet<yourProjectId>
# (a mnemonic derives its own address, so --address is not needed here;
#  equally you could use --signing-key payment.skey --address addr1... on mainnet)
```

**Providers** (where the tool reads UTxOs/params and submits) - choose with `--backend`:

| `--backend` | Networks | Sign-up | Notes |
|-------------|----------|---------|-------|
| `koios` (default) | preprod / preview / mainnet | none | keyless, hosted - just works |
| `blockfrost` | preprod / preview / mainnet | free key | add `--blockfrost-project-id <id>` |
| `yaci` | a LOCAL devnet only | none | a private sandbox on your machine - NOT preprod/mainnet (see below) |

> **Which one do I want?** To record on the real **preprod** testnet or **mainnet**, use the default
> keyless **Koios** (or **Blockfrost**) - no local software, nothing to install. That is exactly how
> the live demo proof above was recorded (`--network preprod`, default Koios). Reach for **Yaci
> DevKit** only when you want a private local playground for development; it is not a public network.

**Keys** - provide exactly one:
- a cardano-cli signing key: `--signing-key payment.skey --address addr_test1v...`, or
- a mnemonic (a secret, so via the environment, never a flag): `export POE_NOTARY_MNEMONIC="word word ..."`.

Every option can also come from an environment variable (`POE_NETWORK`, `POE_BACKEND`,
`POE_BACKEND_URL`, `POE_BLOCKFROST_PROJECT_ID`, `POE_SIGNING_KEY`, `POE_NOTARY_ADDRESS`). Flags win,
then env, then sensible defaults. Run `java -jar notary.jar` with no arguments for the full usage.

## For local development only - Yaci DevKit (not preprod/mainnet)

This section is **only** for experimenting on a private local network. If you want to record a proof
on the real **preprod** or **mainnet**, ignore Yaci entirely and use the `submit` command from the
TL;DR above (default keyless Koios, or Blockfrost) - there is nothing to install.

[Yaci DevKit](https://github.com/bloxbean/yaci-devkit) runs its **own private throwaway Cardano
network** (a devnet) **and** a Blockfrost-compatible API on your machine - instant blocks, a built-in
faucet, no accounts, no public network. It is a development sandbox: proofs recorded on it exist only
on your local devnet and are not visible to public explorers, Koios, or the hosted web page.

```bash
# 1. start a local devnet (Docker); in the Yaci CLI:
yaci-devkit up
create-node -o --start          # starts a devnet; exposes the API at http://localhost:8080/api/v1/
topup addr_test1v...your-addr 1000     # fund an address from the built-in faucet

# 2. record a proof against it (keyless - it is local):
java -jar notary.jar submit myfile.pdf --backend yaci --network testnet \
    --signing-key devnet.skey --address addr_test1v...your-addr
```

`--backend yaci` defaults to `http://localhost:8080/api/v1/`; override with `--backend-url` if your
DevKit uses a different port. (Note: a local devnet is not indexed by public explorers or Koios, so
verify against a local reader or use preprod for something you want the public web page to see.)

## The fully manual way - `cardano-cli`

Because the format is open, you do not need this tool at all. With a running node + `cardano-cli`
(verified with cardano-cli 11.0.0, Conway era, preprod `--testnet-magic 1`):

```bash
# 1. hash your file (any SHA-256 tool works)
H=$(sha256sum myfile.pdf | cut -d' ' -f1)

# 2. write the label-1718 note
cat > proof.json <<JSON
{ "1718": { "h": "$H", "alg": "SHA-256", "ts": "$(date -u +%Y-%m-%dT%H:%M:%SZ)", "name": "myfile.pdf" } }
JSON

# 3. find a UTxO to spend (needs the node socket)
export CARDANO_NODE_SOCKET_PATH=/path/to/node.socket
ADDR=$(cat payment.addr)
cardano-cli query utxo --address "$ADDR" --testnet-magic 1

# 4. build a self-payment that carries the note (auto-balances + computes the fee)
cardano-cli conway transaction build \
    --tx-in <TxHash#Index from step 3> \
    --change-address "$ADDR" \
    --metadata-json-file proof.json \
    --testnet-magic 1 \
    --out-file tx.raw

# 5. sign and submit
cardano-cli conway transaction sign \
    --tx-body-file tx.raw --signing-key-file payment.skey \
    --testnet-magic 1 --out-file tx.signed
cardano-cli conway transaction submit --tx-file tx.signed --testnet-magic 1
```

The resulting transaction is verifiable by the web page and by `notary verify` - it is the same
label-1718 record the tool produces. (`--testnet-magic 1` = preprod, `2` = preview; use `--mainnet`
for mainnet.)

## Verifying a proof

- **Web page (easiest):** open the site, pick the network + transaction, drop in the file. A proof
  link/QR pre-fills everything so a checker only supplies the file. Optionally paste a free Blockfrost
  project id to read the on-chain hash + block time live (browsers cannot read Koios - CORS - so the
  keyless path compares the file to the hash in the link and links out to a public explorer for the
  record).
- **CLI:** `java -jar notary.jar verify myfile.pdf <hash>` (fully local).

---

## Architecture

```
proof-of-existence/
  src/main/java/org/poe/
    DocumentFingerprint.java  # SHA-256 of bytes/files (the fingerprint)
    ProofRecord.java          # the validated { h, alg, ts, name } record
    Notary.java               # builds label-1718 metadata; record(...) submits a tx
    Verifier.java             # re-hash + compare (refuses unknown algorithms)
    Certificate.java          # (optional) CIP-25 certificate metadata builder
    NotaryCli.java            # the CLI: hash / verify / proof / submit (+ provider & key selection)
  onchain/                    # (optional) Aiken on-chain uniqueness registry (aiken check)
  docs/                       # the static web verify UI (GitHub Pages): index.html + poe-verify.js
  chapters/NN-*/README.md     # the course, one step per chapter
```

Two small abstractions keep `submit` simple: a proof is recorded through a single
`Notary.record(backend, address, signer, proof)` path, where `backend` is any bloxbean
`BackendService` (Koios / Blockfrost / Yaci / a local node) and `signer` is any `TxSigner` (from a
`.sk` key or a mnemonic). The CLI just picks which of each to build from your config. See
`chapters/09-cli-submit/README.md` for a full, beginner-friendly walkthrough.

- **On-chain:** SHA-256 hashing is off-chain Java (no contract needed for the core). An optional
  Aiken validator (`onchain/`) can enforce "a hash is notarized at most once" - built and tested, but
  intentionally left unwired as an extension point.
- **Providers:** hosted Koios (keyless) / Blockfrost (keyed), or local Yaci DevKit / your own node.
- **No wallet, no smart contract required** to timestamp a document - that is the project's thesis.

## Build from source

```bash
./gradlew spotlessApply test    # format + unit tests (Java 21; Gradle wrapper included)
./gradlew shadowJar             # build build/libs/notary.jar (the runnable CLI)
node docs/test/verify.test.mjs  # the web UI's logic checks (needs Node)
cd onchain && aiken check       # the optional on-chain registry tests (needs Aiken)
```

## Going to mainnet

Only configuration changes: `--network mainnet`, a mainnet provider (`--backend koios`, or
`--backend blockfrost --blockfrost-project-id <mainnet id>`), and a funded mainnet key. The
hash/record/verify logic is identical - testnet-first, mainnet-portable by design. The one production
upgrade worth knowing is Merkle batching (anchor many document hashes under one root in a single
transaction); see `chapters/07-testnet-wrapup/README.md`.

## What a proof does and does not prove

- **Proves:** this exact document (it reproduces the hash) existed **no later than** the block time,
  and is unchanged.
- **Does not prove:** who authored it, that it did not exist earlier, or anything about its contents
  (only the hash is public).

## License

See the repository. This is a learning/portfolio project; use it, fork it, extend it.
