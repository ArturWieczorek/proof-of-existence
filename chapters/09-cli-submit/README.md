# Chapter 09 - Recording a proof from the CLI: `submit`, providers and keys

> Goal: teach the command-line tool to actually *write* a proof onto the Cardano blockchain, not
> just hash and verify files locally. By the end you will understand, in plain language, what it
> takes to put data on a blockchain, the choices we give you (which "provider", which kind of key),
> why the code is shaped the way it is, and you will see a real proof recorded on a public test
> network from start to finish.
>
> This chapter assumes no blockchain background and no advanced programming. Every term is explained
> the first time it appears.

---

## 1. Where we are, and what is missing

In the earlier chapters the tool learned three things, all of which happen entirely **on your own
computer**, with no internet and no account anywhere:

- `hash <file>`   - compute a document's fingerprint (a SHA-256 hash).
- `verify <file> <hash>` - re-hash a file and check it matches a fingerprint.
- `proof <file>`  - print the little record we *would* put on-chain.

What was missing is the one step that is **not** local: actually **recording** the proof on the
blockchain so it gets a permanent, tamper-proof timestamp that anyone can later check. That is what
this chapter adds:

```
notary submit <file> [name]
```

Everything else in the tool stays offline and keyless. Only `submit` reaches out to the network.

---

## 2. Plain-language background (read this once)

### 2.1 What does "record on the blockchain" actually mean?

The Cardano blockchain is a giant, shared, append-only ledger. You cannot "save a file" to it. What
you *can* do is create a **transaction** (a small signed message that normally moves some coins) and
attach a little note to it, called **metadata**. Our proof is exactly that note:

```json
{ "h": "<the document's SHA-256 hash>", "alg": "SHA-256", "ts": "<time>", "name": "<label>" }
```

We file this note under a fixed number, **label 1718** (our notary's "drawer" in the metadata
cabinet). The transaction itself just sends 1 test-ADA from you back to you - it exists only to carry
the note. Once that transaction is included in a block, the note is permanent and dated by the
network. Nobody can backdate it or change it.

Two important consequences (they come back later):

- The document never leaves your computer - only its hash does.
- The proof is the note on the ledger. Everything else (this tool, the website, your key) is just
  machinery to *write* and *find* it.

### 2.2 What is a "provider", and why do we need one?

To build and send that transaction, a program needs three pieces of live information from the
network:

1. **Your unspent coins (UTxOs)** - which coins you own, so it can use one to pay the tiny fee.
2. **The current protocol parameters** - the network's current rules (e.g. how fees are calculated),
   so the transaction it builds is valid.
3. **A way to submit** - to broadcast the finished transaction to the network.

Our tool is built on a Java library called **bloxbean** (the `cardano-client-lib`). This library can
*build and sign* transactions, but it is **not a Cardano node** - it has no direct connection to the
network. So it asks a **provider**: an online (or local) service that answers those three questions
over the web. Think of a provider as a receptionist at the blockchain's front desk: you do not walk
into the vault yourself, you ask the desk.

We support three providers (more on choosing in section 4):

- **Koios** - free, no sign-up ("keyless"). The default.
- **Blockfrost** - free but you register once and get an API key.
- **Yaci DevKit** - a provider that runs on *your own machine* for a private local test network.

### 2.3 What is a "key", and what does it do here?

To create a transaction that spends even 1 test-ADA, you must **sign** it with a private **key** -
proof that you are allowed to spend from that address. In this tool the key does two small jobs:
authorize the transaction, and pay its fee.

Crucially, the key is **not part of the proof**. It is only "who paid for the stamp". If you lose the
key, your past proofs are still valid forever, and you can make new ones with any other funded key.
Verification never uses a key at all (see section 8).

We accept the key in two common forms:

- A **cardano-cli signing key file** (`something.sk`) - a small JSON file produced by Cardano's
  official command-line tools.
- A **mnemonic** - the familiar 12/15/24 secret words that wallets use.

---

## 3. The code, and *why* it is shaped this way

Here is the key design idea, in one sentence: **there is only one function that records a proof, and
it does not care which provider or which key you use.** We achieve that with two Java "interfaces".

### 3.1 What is an interface (and why it helps)?

An **interface** in Java is a promise about *what* something can do, without saying *how*. A good
real-world analogy is a **power socket**: any appliance with the right plug works, whether it is a
lamp or a laptop charger; the wall does not care. In code, an interface lets us write one piece of
logic that works with *any* implementation that "fits the plug".

We lean on two interfaces that the bloxbean library already defines:

- **`BackendService`** - the "provider plug". Koios, Blockfrost, and Yaci each provide a class that
  fits this plug (`KoiosBackendService`, `BFBackendService`). Our recording code accepts *any*
  `BackendService`, so it does not contain a single `if (koios) ... else ...`.
- **`TxSigner`** - the "signer plug". A mnemonic account and a raw signing key can each be turned
  into a `TxSigner`. Our recording code accepts *any* `TxSigner`.

Because both varying parts hide behind a plug, the actual recording function is tiny and has **no
branches**:

```java
// Notary.java - the single code path both key kinds and all providers go through.
public static TxResult record(
    BackendService backend, String senderAddress, TxSigner signer, ProofRecord proof) {
  Tx tx =
      new Tx()
          .payToAddress(senderAddress, Amount.ada(1)) // a 1-ADA self-payment...
          .attachMetadata(buildMetadata(proof))       // ...carrying the proof note (label 1718)
          .from(senderAddress);
  return new QuickTxBuilder(backend).compose(tx).withSigner(signer).complete();
}
```

Read it in English: "using this provider, build a transaction that pays 1 ADA from my address back
to my address, attach the proof note, sign it with this signer, and complete (build, sign, submit)."
It never mentions Koios, Blockfrost, `.sk`, or mnemonics - those choices were already made *before*
we got here, and turned into a `backend` and a `signer`.

The older `record(backend, account, proof)` from Chapter 02 still exists; we just made it call the
new one, so nothing that used it broke:

```java
public static TxResult record(BackendService backend, Account notary, ProofRecord proof) {
  return record(backend, notary.baseAddress(), SignerProviders.signerFrom(notary), proof);
}
```

### 3.2 Turning your choices into a `backend` and a `signer` (small "factory" methods)

A **factory** is just a method whose job is to *construct* the right object for you. We have two.

**`backendFor`** builds the provider you asked for. Notice all three branches return the same type
(`BackendService`), which is why the recorder above can stay ignorant of the choice:

```java
static BackendService backendFor(SubmitConfig cfg) {
  return switch (cfg.backend()) {
    case BLOCKFROST -> new BFBackendService(cfg.backendUrl(), cfg.blockfrostProjectId());
    case YACI       -> new BFBackendService(cfg.backendUrl(),                 // Yaci speaks the
        cfg.blockfrostProjectId() == null ? "yaci" : cfg.blockfrostProjectId()); // Blockfrost API
    case KOIOS      -> new KoiosBackendService(cfg.backendUrl());
  };
}
```

Why does **Yaci reuse `BFBackendService`?** Because Yaci DevKit deliberately exposes the *same* web
API shape as Blockfrost. So "supporting Yaci" is not a whole new provider - it is just Blockfrost
pointed at a local address (`http://localhost:8080/api/v1/`) with a throwaway project id. Reusing the
existing class is less code and less that can break.

**`senderFor`** turns your key into an address + a signer:

```java
static Sender senderFor(SubmitConfig cfg) {
  if (cfg.signingKeyFile() != null) {                       // a cardano-cli .sk file
    SecretKey key = loadSecretKey(cfg.signingKeyFile());
    return new Sender(cfg.senderAddress(), SignerProviders.signerFrom(key));
  }
  Account account = new Account(cfg.network(), cfg.mnemonic()); // a mnemonic
  return new Sender(account.baseAddress(), SignerProviders.signerFrom(account));
}
```

Both branches end with `SignerProviders.signerFrom(...)`, which returns a `TxSigner` - the common
"plug". A mnemonic can compute its own address; a raw `.sk` cannot, so for a `.sk` you also pass the
`--address` (the tool cannot guess which address a bare key belongs to).

### 3.3 Reading a cardano-cli key file

A cardano-cli signing key file looks like this:

```json
{
  "type": "PaymentSigningKeyShelley_ed25519",
  "description": "Payment Signing Key",
  "cborHex": "5820e3b0c44298fc1c149afbf4c8996fb924...."
}
```

We only need the `cborHex` value (the actual key material, encoded). We read it and hand it to
bloxbean's `SecretKey`:

```java
static SecretKey loadSecretKey(Path path) {
  Matcher m = CBOR_HEX.matcher(Files.readString(path)); // find "cborHex": "...."
  if (!m.find()) throw new IllegalStateException("not a cardano-cli signing key (no cborHex): " + path);
  return new SecretKey(m.group(1));
}
```

We did **not** modify the bloxbean library to do this - `SecretKey` and `signerFrom(SecretKey)`
already exist. We only wrote the little glue that reads the file. (A subtle real-world detail: keys
made by `cardano-cli` are "normal" ed25519 keys; we confirmed by an actual on-chain submit that
bloxbean signs them correctly - see section 7.)

### 3.4 Where the settings come from: flags, then environment, then defaults

The tool must be pleasant both for a first-time user and for scripts/servers. So every setting can be
given three ways, in priority order:

1. A **command-line flag** (e.g. `--network preprod`) - wins if present.
2. An **environment variable** (e.g. `POE_NETWORK=preprod`) - used if no flag.
3. A sensible **default** (e.g. `preprod`, `koios`) - used if neither is set.

A flag is text you type after the command; an environment variable is a named value your shell holds
(you set it with `export NAME=value`). This "flag beats env beats default" rule lives in one small
helper:

```java
private static String pick(Map<String,String> flags, String flagKey,
                           Map<String,String> env, String envKey, String def) {
  String fromFlag = flags.get(flagKey);
  if (!isBlank(fromFlag)) return fromFlag;      // 1. a flag wins
  String fromEnv = env.get(envKey);
  return isBlank(fromEnv) ? def : fromEnv;      // 2. else env, 3. else default
}
```

**Why is the mnemonic the one setting that is env-only (never a flag)?** Because it is a secret.
Anything you type as a flag is visible in your shell history and to other programs (via the process
list). Secrets belong in an environment variable or a file, not on the command line. The `.sk` is
passed as a *file path* (the path is not secret; the file's contents stay on disk).

### 3.5 How we made this testable without a real network: the "seam"

We want to test the `submit` command without actually paying to broadcast a transaction every time.
The trick is a **seam** - a single place where we can swap the real, network-touching part for a fake
one during tests. It is a one-method interface:

```java
@FunctionalInterface
interface ProofSubmitter {
  String submit(ProofRecord proof, Map<String,String> flags) throws Exception; // returns a tx hash
}
```

The real program uses a submitter that talks to the network; a test passes a fake submitter that just
returns a made-up transaction hash and records what it was given. This is called **dependency
injection** - "inject" the behaviour from outside instead of hard-wiring it. It let us prove the
command builds the right proof and prints the right output, with **no network and no key**.

Separately, the whole settings-resolution logic (`resolveConfig`) is a **pure function**: it takes
the flags and the environment as plain maps and returns a settings object, touching nothing external.
Pure functions are the easiest thing in the world to test - give inputs, assert the output.

---

## 4. Choosing a provider (and a browser-vs-CLI surprise)

| Provider | Sign-up? | Runs where | Use it when |
|----------|----------|-----------|-------------|
| **Koios** (default) | none (keyless) | hosted | you just want it to work with zero setup |
| **Blockfrost** | free API key | hosted | you already have a key, or want it for mainnet |
| **Yaci DevKit** | none | your machine | you want a private local test network, no third party |

A genuinely surprising fact we hit while building Chapter 08 (the web page) and confirmed by
experiment: **a web browser cannot use Koios, but this command-line tool can.** Browsers enforce a
security rule called CORS that requires the server to opt in with a special header; Koios does not
send it, so browser calls are blocked. Command-line programs are not subject to CORS, so the CLI
happily uses keyless Koios. That is exactly why the CLI defaults to Koios, while the web page uses
Blockfrost or the keyless link-plus-explorer approach.

**Local, no third party at all.** If you do not want to rely on any hosted service, run
[Yaci DevKit](https://github.com/bloxbean/yaci-devkit): it starts a private Cardano network and a
Blockfrost-compatible provider on your own machine. Because it copies the Blockfrost API, our tool
needs *no new code* - you just point it locally:

```bash
notary submit contract.pdf --backend yaci      # defaults to http://localhost:8080/api/v1/
# equivalently, the long way with the blockfrost backend:
notary submit contract.pdf --backend blockfrost --backend-url http://localhost:8080/api/v1/ \
  --blockfrost-project-id anything
```

*(A note for later: a fully local setup against a node you sync yourself can also be done with Ogmios
+ Kupo, or with a small adapter over `cardano-cli` + `cardano-submit-api`. We deliberately did not
build those yet; the `BackendService` "plug" makes adding an `--backend ogmios` a small future change.
`cardano-submit-api` on its own only broadcasts - it cannot answer the "what are my coins / what are
the rules" questions, so it is not enough by itself.)*

---

## 5. Using `submit`: worked examples

The tool prints its own help if you run it with no arguments. The `submit` options:

```
notary submit <file> [name]
  --network preprod|preview|mainnet|testnet   (env POE_NETWORK, default preprod)
  --backend koios|blockfrost|yaci             (env POE_BACKEND, default koios)
  --backend-url <url>                          (env POE_BACKEND_URL, default per backend; yaci -> localhost)
  --blockfrost-project-id <id>                 (env POE_BLOCKFROST_PROJECT_ID; blockfrost only)
  --signing-key <file.sk> --address <addr>     (a cardano-cli key; env POE_SIGNING_KEY/POE_NOTARY_ADDRESS)
  (or) POE_NOTARY_MNEMONIC=<mnemonic>          (a mnemonic; env only, it is a secret)
```

**Example A - keyless Koios on preprod, with a cardano-cli key** (this is the exact shape we ran):

```bash
notary submit contract.txt "my-contract" \
  --network preprod \
  --signing-key /path/to/my-preprod.sk \
  --address addr_test1vqm0tkt55qhpqwrc0pftux0q42geqdc0ngzdd0tk00ftw8q2sg952
```

**Example B - Blockfrost on mainnet, with a mnemonic** (secret stays in the environment):

```bash
export POE_NOTARY_MNEMONIC="word word word ... word"
notary submit contract.txt \
  --network mainnet --backend blockfrost --blockfrost-project-id mainnetXXXXXXXX
```

**Example C - a local Yaci DevKit devnet** (no accounts anywhere):

```bash
export POE_NOTARY_MNEMONIC="<one of Yaci's pre-funded test mnemonics>"
notary submit contract.txt --backend yaci --network testnet
```

On success the tool prints one line:

```
submitted: tx=<64-hex transaction hash> hash=<document hash> name=<name>
```

If something is misconfigured it fails clearly instead of dumping a stack trace, e.g.
`submit failed: blockfrost backend needs --blockfrost-project-id (or POE_BLOCKFROST_PROJECT_ID)`.

---

## 6. A real bug our tests caught (worth seeing)

While unit-testing the provider factory, one test failed with `IllegalArgumentException`. The cause:
the Koios client is built on a networking library (Retrofit) that **requires the base URL to end with
a `/`**. Our default Koios URL was `https://preprod.koios.rest/api/v1` (no trailing slash), so it
threw at construction time. We added the slash:

```java
// before: "https://preprod.koios.rest/api/v1"   -> crash
// after:  "https://preprod.koios.rest/api/v1/"   -> works
```

This is the whole point of writing tests first: the failure surfaced on our machine in milliseconds,
instead of as a confusing crash the first time a user tried to submit.

---

## 7. Proof that it works: a real notarization on preprod

We did not just assert this works - we recorded a real proof on the public **preprod** test network
and checked it end to end.

**Step 1 - submit** (keyless Koios, using a cardano-cli `.sk`):

```
$ notary submit preprod-proof-demo.txt poe-ch08-demo --network preprod \
    --signing-key my-preprod.sk --address addr_test1vqm0tkt...q2sg952
submitted: tx=1d65dd63b4e091977742e7077197bee48554dd259a892f42c053234760032fcc \
           hash=267219dda604092c126441daa8234ddad19882699ed0b8e889a449c32fd1dcd4 name=poe-ch08-demo
```

**Step 2 - read it back from the chain** (a few seconds later, once a block included it):

```json
// the transaction's metadata under label 1718, straight from the network:
{ "h": "267219dda604092c126441daa8234ddad19882699ed0b8e889a449c32fd1dcd4",
  "alg": "SHA-256", "ts": "2026-07-12T17:01:49.936476714Z", "name": "poe-ch08-demo" }
// block height 4928405, block time 2026-07-12T17:02:05Z
```

The on-chain `h` equals the file's SHA-256 - the proof is real and correct.

**Step 3 - verify it both ways:**

- The Chapter 08 web page, opened at the proof's share-link and given the same file, showed
  **MATCH**, with a working "view the raw on-chain record" link to that transaction.
- The CLI agreed:

```
$ notary verify preprod-proof-demo.txt 267219dda604092c126441daa8234ddad19882699ed0b8e889a449c32fd1dcd4
MATCH
```

This also settled the one thing we could not know without running it: a normal cardano-cli ed25519
key signs correctly through bloxbean. It does.

---

## 8. Reassurance: what happens if the provider is down, or you lose the key

- **Provider down** - you cannot `submit` at that moment. Switch provider (`--backend`), point at a
  local Yaci, or retry later. **Proofs already on the chain are untouched**, and verifying an existing
  proof does not need our provider at all (the re-hash is local; the record is visible in any
  explorer).
- **Key lost** - you simply notarize future documents with any other funded key. **Past proofs stand
  forever**, and verification never uses a key. The key is "who paid for the stamp", not the stamp.

This is the deep reason the notary is robust: the valuable thing (the dated hash on a public ledger)
does not depend on any company, server, or key staying alive.

---

## 9. What we changed in this chapter (file by file)

- `build.gradle.kts` - added one dependency, `cardano-client-backend-koios`, so the CLI can use the
  keyless Koios provider (Blockfrost was already there; Yaci reuses it).
- `Notary.java` - added the branch-free `record(backend, address, signer, proof)` overload; the old
  `record(backend, account, proof)` now delegates to it.
- `NotaryCli.java` - the `submit` command; flag/env/default resolution (`resolveConfig`); the provider
  and signer factories (`backendFor`, `senderFor`); the `.sk` reader (`loadSecretKey`); the testable
  `ProofSubmitter` seam.
- `NotaryCliTest.java` - offline tests for all of the above (see section 10).

---

## 10. The tests (all offline) and how to run them

```bash
./gradlew spotlessApply test
```

What they check, in plain terms:

- `submit` produces the right proof and prints the returned transaction hash (via the fake submitter).
- Settings resolution: a flag beats an environment variable beats a default; you must supply a key;
  a `.sk` also needs an `--address`; Blockfrost needs a project id; Yaci needs neither and defaults to
  a local URL.
- The provider factory builds the correct class for each choice (Koios / Blockfrost / Yaci).
- A mnemonic yields the same address bloxbean derives; a `.sk` file is read correctly.

The actual network submit is an integration step (it needs a funded key), kept separate from the fast
offline unit tests - and demonstrated for real in section 7.

---

## 11. Going to mainnet

Nothing in the logic changes. You switch configuration: `--network mainnet`, a mainnet provider
(`--backend blockfrost --blockfrost-project-id <mainnet id>`, or keyless `--backend koios`), and a
funded mainnet key. The hash/record/verify behaviour is identical, because network and provider are
configuration, never baked into the code.

---

## 12. What is next (optional)

- `--backend ogmios` for a fully local node setup (Ogmios + Kupo). The `BackendService` plug makes it
  a small, additive change.
- A downloadable binary (a runnable jar, or a `jpackage` app image; a true native binary via GraalVM
  later) so non-developers can use `submit` without installing a toolchain.
- Publishing the metadata format (label 1718) on the web page, so any tool can create or read a proof
  - the tool is a convenience, not a lock-in.

## Glossary (Chapter 09)

- **Transaction** - a signed message sent to the blockchain; ours carries the proof as a note.
- **Metadata / label 1718** - the note attached to a transaction; 1718 is the drawer we file proofs
  in.
- **Provider / backend** - a service (hosted or local) that answers "what coins do I have", "what are
  the current rules", and "please broadcast this" so the tool can build and send a transaction.
- **Koios / Blockfrost / Yaci DevKit** - a keyless hosted provider / a keyed hosted provider / a local
  provider you run yourself.
- **CORS** - a browser security rule that blocks calls to servers that do not opt in; it blocks Koios
  in a browser but does not affect a command-line tool.
- **Key / signing** - the private secret that authorizes spending; it pays the fee and signs the
  transaction, but is never part of the proof.
- **`.sk` file vs mnemonic** - two forms of key: a cardano-cli signing-key file, or the secret words a
  wallet uses.
- **Interface (Java)** - a "plug" describing what something can do, not how; lets one function work
  with many implementations (`BackendService`, `TxSigner`).
- **Factory** - a method whose job is to construct the right object (`backendFor`, `senderFor`).
- **Seam / dependency injection** - a swappable point (`ProofSubmitter`) that lets tests replace the
  network-touching part with a fake.
- **Pure function** - a function whose output depends only on its inputs (`resolveConfig`), which
  makes it trivial to test.
- **Flag vs environment variable** - a setting typed on the command line vs one held by your shell;
  flags win, then env, then defaults.
