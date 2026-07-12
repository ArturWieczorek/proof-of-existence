# Chapter 10 - Publish: a public site, a runnable binary, and an open format

> Goal: ship it to the world. Make the verify page publicly reachable (GitHub Pages), give people a
> one-file download of the recording tool (a runnable `notary.jar`), and document the proof *format*
> on the page so anyone can create or read a proof with any tool - not just ours.

## 1. Why this chapter

A notary is only useful if (a) anyone can *verify* without installing anything, and (b) the way
proofs are made is *open*, so nobody is locked into our software. This chapter delivers both, and a
convenient binary for people who do want to record proofs but not clone a repo.

## 2. What we do
- **Public site.** The repo is made public and GitHub Pages serves `docs/` (branch `main`, folder
  `/docs`). The verify page then lives at `https://<user>.github.io/<repo>/`. (Free Pages requires a
  public repo; a private repo would need a paid plan or a separate public docs repo.)
- **A runnable binary.** The `shadow` Gradle plugin builds one self-contained *fat jar* that bundles
  every dependency, so users run `java -jar notary.jar <command>` with only Java 21 installed:
  ```bash
  ./gradlew shadowJar        # -> build/libs/notary.jar
  ```
  It is attached to a GitHub **Release**, so the site can link to a stable download:
  `https://github.com/<user>/<repo>/releases/latest/download/notary.jar`.
- **An open format on the page.** The verify page gained a "How are these proofs created?" section
  documenting the metadata note (label 1718) and three ways to produce it - our CLI, any wallet /
  `cardano-cli` that can attach metadata, or a local node - plus the download link.

## 3. The format we publish (label 1718)
```json
{ "1718": { "h": "<sha256 hex>", "alg": "SHA-256", "ts": "<iso-8601>", "name": "<label>" } }
```
Attach that to any Cardano transaction's metadata and submit it; this page (and the CLI) will verify
it. That is the whole contract - the tool is a convenience, not a lock-in.

## 4. Steps (what was run)
```bash
# 1. build + prove the binary works (offline hash, and a real preprod submit)
./gradlew shadowJar
java -jar build/libs/notary.jar hash somefile          # matches the known SHA-256
java -jar build/libs/notary.jar submit somefile --network preprod --signing-key k.sk --address addr_test1v...

# 2. make the repo public, push, and release the jar
gh repo edit --visibility public --accept-visibility-change-consequences
git push origin main --tags
gh release create v0.1.0 build/libs/notary.jar --title "notary v0.1.0" --notes "Runnable CLI: java -jar notary.jar ..."

# 3. turn on GitHub Pages (main / docs)
gh api -X POST repos/<user>/<repo>/pages -f 'source[branch]=main' -f 'source[path]=/docs'
```

## 5. What to notice / common mistakes
- **Public is (effectively) forever.** Anything published can be cloned/cached/forked. We ran a
  secrets audit over the whole tree + git history before flipping the repo public (it was clean); do
  the same before publishing anything. Your git commit author email becomes public too.
- **The fat jar must actually work.** Bundling many libraries into one jar can break service-loading;
  we verified `notary.jar` both hashes locally *and* submits a real transaction, not just that it
  builds.
- **Verifying is zero-install; creating is not.** The web page verifies with nothing installed.
  Recording still needs a key + a small fee, so the "easy" creation path is the downloadable jar (or,
  later, a browser wallet flow) - a hosted website cannot sign for you.
- **Keyless by default.** `submit` defaults to the keyless Koios provider, so a new user needs no
  account to record a proof - only a funded key.

## 6. Wrap-up
Proof of Existence is now a complete, public, self-describing tool: a static verify page anyone can
use, a one-file CLI to create proofs (keyless by default, or Blockfrost, or a local node), an
optional live on-chain read, and a documented open format so it is never a black box. Optional
chapters 05 (on-chain uniqueness registry) and 06 (NFT certificate) remain as built-and-tested
extension points for forks.

## Glossary (Chapter 10)
- **GitHub Pages** - free static hosting served from a repo (here `main` / `docs`); needs a public
  repo on the free plan.
- **Fat jar (uber jar)** - a single jar bundling the program and all its dependencies, runnable with
  `java -jar`.
- **`shadow` plugin** - the Gradle plugin that builds the fat jar (and merges service files so
  bundled libraries keep working).
- **Release** - a tagged, downloadable bundle on GitHub; we attach `notary.jar` to it.
- **Open format** - a published, tool-independent spec (label 1718) so anyone can create or read a
  proof.
