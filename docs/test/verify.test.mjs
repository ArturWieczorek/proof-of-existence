// Chapter 08 verification - runs the SHIPPED page logic, no external dependencies.
//   node docs/test/verify.test.mjs
//
// This imports the exact files the page loads (../poe-verify.js and ../vendor/qrcode.js) and checks
// hashing, the keyless compare, the algorithm-aware compare, Blockfrost proof parsing, the
// Blockfrost fetch path (with an injected fetch so it is deterministic and offline), the deep-link
// round-trip, and QR encoding. Node provides the same crypto.subtle the browser uses, so sha256Hex
// is exercised for real. (Additional facts established during development and documented in the
// chapter README: byte-identical to the Java CLI on a real file, the QR decoded back by an
// independent reader, the real page driven in headless Chromium, and Koios being CORS-blocked.)
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const M = await import(join(here, "..", "poe-verify.js"));
const qrcode = require(join(here, "..", "vendor", "qrcode.js"));

let pass = 0, fail = 0;
const ok = (name, cond) => { cond ? (pass++, console.log("ok   - " + name)) : (fail++, console.log("FAIL - " + name)); };
const enc = (s) => new TextEncoder().encode(s);

// SHA-256 known-answer tests (the same vectors the Java DocumentFingerprint test uses).
ok("sha256('') KAT", (await M.sha256Hex(enc(""))) === "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
ok("sha256('abc') KAT", (await M.sha256Hex(enc("abc"))) === "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
const H = await M.sha256Hex(enc("proof of existence"));

// Keyless compare (file vs the hash carried in the proof link/QR).
ok("compareHash MATCH", M.compareHash(H, H).status === "MATCH");
ok("compareHash NO_MATCH", M.compareHash(H, "a".repeat(64)).status === "NO_MATCH");
ok("compareHash NO_EXPECTED_HASH", M.compareHash(H, "").status === "NO_EXPECTED_HASH");
ok("compareHash is case-insensitive", M.compareHash(H.toUpperCase(), H).status === "MATCH");

// Algorithm-aware compare + Blockfrost proof parsing (label 1718 in the real /metadata shape).
const bfMeta = [
  { label: "674", json_metadata: { msg: ["hi"] } },
  { label: "1718", json_metadata: { h: H, alg: "SHA-256", ts: "2026-07-12T10:00:00Z", name: "doc.txt" } },
];
const proof = M.parseBlockfrostProof(bfMeta);
ok("parseBlockfrostProof finds 1718", proof && proof.h === H && proof.name === "doc.txt");
ok("compareProof MATCH", M.compareProof(H, proof).status === "MATCH");
ok("compareProof NO_MATCH", M.compareProof("b".repeat(64), proof).status === "NO_MATCH");
ok("compareProof UNSUPPORTED_ALG", M.compareProof(H, { h: H, alg: "blake2b-256" }).status === "UNSUPPORTED_ALG");
ok("parseBlockfrostProof null without 1718", M.parseBlockfrostProof([{ label: "674", json_metadata: {} }]) === null);
ok("parseBlockfrostProof null on bad hash", M.parseBlockfrostProof([{ label: "1718", json_metadata: { h: "nope" } }]) === null);

// Blockfrost fetch path with an injected fetch: right URLs, project_id header, parsing, 404.
const calls = [];
const fake = async (url, opts) => {
  calls.push({ url, headers: opts && opts.headers });
  if (url.endsWith("/metadata")) return { ok: true, status: 200, json: async () => bfMeta };
  return { ok: true, status: 200, json: async () => ({ block_time: 1783866632, block_height: 4928014 }) };
};
const tx = "a".repeat(64);
const res = await M.fetchProofBlockfrost("preprod", tx, "preprodKEY", fake);
ok("fetch hits /txs/<tx>/metadata + /txs/<tx>",
  calls.some((c) => c.url === "https://cardano-preprod.blockfrost.io/api/v0/txs/" + tx + "/metadata") &&
  calls.some((c) => c.url === "https://cardano-preprod.blockfrost.io/api/v0/txs/" + tx));
ok("fetch sends project_id header", calls.every((c) => c.headers && c.headers.project_id === "preprodKEY"));
ok("fetch parses proof + block time + height", res.found && res.proof.h === H && res.blockTime === 1783866632 && res.blockHeight === 4928014);
ok("fetch builds explorer URL", res.explorer === "https://preprod.cardanoscan.io/transaction/" + tx);
const res404 = await M.fetchProofBlockfrost("preprod", "c".repeat(64), "k", async () => ({ status: 404, ok: false }));
ok("fetch treats 404 as not-found", res404.found === false);

// Deep-link round-trip (the payload the QR carries).
const link = M.buildDeepLink("https://example.github.io/poe/", "preprod", tx, H);
const dl = M.parseDeepLink("#" + link.split("#")[1]);
ok("deep-link round-trips", dl && dl.network === "preprod" && dl.tx === tx && dl.h === H);
ok("parseDeepLink rejects non-verify hash", M.parseDeepLink("#nope") === null);

// QR encodes the deep-link to a valid, non-empty matrix.
const qr = qrcode(0, "M");
qr.addData(link); qr.make();
ok("QR encodes deep-link (non-empty matrix)", qr.getModuleCount() >= 21 && qr.isDark(0, 0) === true);

console.log("\n" + pass + " passed, " + fail + " failed");
process.exit(fail ? 1 : 0);
