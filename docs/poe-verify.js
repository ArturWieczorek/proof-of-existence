// Proof of Existence - browser verify logic (Chapter 08).
//
// PURE and side-effect-free at import time (no DOM), so the exact same file is loaded by the page
// (index.html) AND executed by the Node test harness. Relies only on Web Platform globals present
// in both: crypto.subtle and fetch. Verification is read-only - we never hold a key that can spend
// and never write to the chain.
//
// The proof lives in transaction metadata under label 1718 (see Notary.METADATA_LABEL):
//   { "1718": { "h": <64 hex>, "alg": "SHA-256", "ts": <ISO-8601>, "name": <string> } }
//
// TWO ways to get the on-chain hash to compare against:
//   1. Keyless (default): the expected hash rides in the proof link/QR (net+tx+h). We re-hash the
//      file locally and compare; the explorer link lets anyone confirm that hash really is the one
//      recorded on-chain at the shown time. No key, no backend, works on plain static hosting.
//   2. Optional live read: paste a free Blockfrost project id. Browsers CANNOT read Koios (it sends
//      no Access-Control-Allow-Origin on responses, so CORS blocks it); Blockfrost is CORS-enabled.
//      We then read the on-chain proof + block time directly and cross-check it against the link.

export const PROOF_LABEL = "1718";

// The hash algorithm this tool can recompute (mirrors DocumentFingerprint.ALGORITHM / Verifier).
export const ALGORITHM = "SHA-256";

// Blockfrost is CORS-friendly (verified from a browser); Koios is not, so it cannot be used here.
export const BLOCKFROST = {
  preprod: "https://cardano-preprod.blockfrost.io/api/v0",
  preview: "https://cardano-preview.blockfrost.io/api/v0",
  mainnet: "https://cardano-mainnet.blockfrost.io/api/v0",
};

// A trusted public explorer, per network (a convenience wrapper over the durable tx hash).
export const EXPLORER = {
  preprod: "https://preprod.cardanoscan.io/transaction/",
  preview: "https://preview.cardanoscan.io/transaction/",
  mainnet: "https://cardanoscan.io/transaction/",
};

const HEX_64 = /^[0-9a-f]{64}$/;
const TX_HASH = /^[0-9a-f]{64}$/;

/** SHA-256 of the given bytes as 64 lowercase hex chars. Byte-identical to the Java notary. */
export async function sha256Hex(bytes) {
  const buf =
    bytes instanceof ArrayBuffer
      ? bytes
      : bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength);
  const digest = await crypto.subtle.digest("SHA-256", buf);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/** Keyless compare: does the file's fingerprint equal the expected hash (from the proof link/QR)? */
export function compareHash(fileHex, expectedHex) {
  if (!HEX_64.test(String(fileHex).toLowerCase())) return { status: "BAD_FILE_HASH" };
  if (!HEX_64.test(String(expectedHex || "").toLowerCase())) return { status: "NO_EXPECTED_HASH" };
  return { status: fileHex.toLowerCase() === expectedHex.toLowerCase() ? "MATCH" : "NO_MATCH" };
}

/**
 * Compare a file's fingerprint to a parsed on-chain proof. Mirrors Verifier.verify: the algorithm
 * must be one we can recompute (SHA-256), else refuse loudly rather than guess.
 */
export function compareProof(fileHex, proof) {
  if (!proof || proof.alg !== ALGORITHM) return { status: "UNSUPPORTED_ALG" };
  return { status: fileHex.toLowerCase() === proof.h.toLowerCase() ? "MATCH" : "NO_MATCH" };
}

/** Extract the label-1718 proof from a Blockfrost /txs/{hash}/metadata array. @returns {h,alg,ts,name}|null */
export function parseBlockfrostProof(metadataArray) {
  if (!Array.isArray(metadataArray)) return null;
  const row = metadataArray.find((r) => r && String(r.label) === PROOF_LABEL);
  const map = row && row.json_metadata;
  if (!map || typeof map !== "object") return null;
  const h = typeof map.h === "string" ? map.h.toLowerCase() : "";
  if (!HEX_64.test(h)) return null;
  return {
    h,
    alg: typeof map.alg === "string" ? map.alg : "",
    ts: map.ts == null ? "" : String(map.ts),
    name: map.name == null ? "" : String(map.name),
    description: map.description == null ? "" : String(map.description),
  };
}

export function explorerUrl(network, txHash) {
  const base = EXPLORER[network];
  return base ? base + txHash : null;
}

/**
 * Optional live read via Blockfrost (CORS-enabled). Read-only: the project id only grants reads.
 * @returns {found, proof, blockTime, blockTimeIso, blockHeight, explorer, txHash, network}
 */
export async function fetchProofBlockfrost(network, txHash, projectId, fetchImpl = fetch) {
  if (!BLOCKFROST[network]) throw new Error("unknown network: " + network);
  if (!TX_HASH.test(txHash)) throw new Error("tx hash must be 64 hex chars");
  if (!projectId) throw new Error("a Blockfrost project id is required for live read");
  const base = BLOCKFROST[network];
  const get = async (path) => {
    const r = await fetchImpl(base + path, { headers: { project_id: projectId } });
    if (r.status === 404) return null; // tx or metadata not found
    if (!r.ok) throw new Error("Blockfrost " + path + " HTTP " + r.status);
    return r.json();
  };
  const [meta, info] = await Promise.all([get("/txs/" + txHash + "/metadata"), get("/txs/" + txHash)]);
  const proof = parseBlockfrostProof(meta);
  const blockTime = info && typeof info.block_time === "number" ? info.block_time : null;
  return {
    found: proof != null,
    proof,
    blockTime,
    blockTimeIso: blockTime != null ? new Date(blockTime * 1000).toISOString() : null,
    blockHeight: (info && info.block_height) ?? null,
    explorer: explorerUrl(network, txHash),
    txHash,
    network,
  };
}

/**
 * Build a shareable verify deep-link carrying the durable txHash + expected hash. The QR encodes
 * exactly this string; opening it pre-fills the page so a checker only has to drop in the file.
 *   <base>#verify?net=<network>&tx=<txHash>&h=<hash>
 */
export function buildDeepLink(baseUrl, network, txHash, h) {
  const b = baseUrl.split("#")[0];
  const q = `net=${encodeURIComponent(network)}&tx=${encodeURIComponent(txHash)}&h=${encodeURIComponent(h)}`;
  return `${b}#verify?${q}`;
}

/** Parse a deep-link's location.hash (with or without leading '#'). @returns {network,tx,h} | null */
export function parseDeepLink(hash) {
  if (!hash) return null;
  const s = hash.replace(/^#/, "");
  if (!s.startsWith("verify?")) return null;
  const p = new URLSearchParams(s.slice("verify?".length));
  const tx = (p.get("tx") || "").toLowerCase();
  const h = (p.get("h") || "").toLowerCase();
  const network = p.get("net") || "preprod";
  return {
    network: BLOCKFROST[network] ? network : "preprod",
    tx: TX_HASH.test(tx) ? tx : "",
    h: HEX_64.test(h) ? h : "",
  };
}
