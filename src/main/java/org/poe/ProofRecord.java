package org.poe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * The data we record on-chain for one notarization: the document's hash, the algorithm, a
 * timestamp, and an optional short name/label. Deliberately tiny - only the hash, never the
 * document.
 *
 * @param hashHex 64 lowercase hex chars (a SHA-256 digest)
 * @param algorithm the hash algorithm name (e.g. "SHA-256")
 * @param timestamp ISO-8601 instant string (UTC), e.g. "2026-06-30T12:00:00Z"
 * @param name an optional human label (empty if none); max 64 bytes (Cardano metadata string limit)
 */
public record ProofRecord(String hashHex, String algorithm, String timestamp, String name) {

  private static final Pattern HEX_64 = Pattern.compile("^[0-9a-f]{64}$");

  /** The largest a single Cardano transaction-metadata text value may be. */
  public static final int MAX_METADATA_STRING_BYTES = 64;

  public ProofRecord {
    if (hashHex == null || !HEX_64.matcher(hashHex).matches()) {
      throw new IllegalArgumentException("hashHex must be 64 lowercase hex chars: " + hashHex);
    }
    if (algorithm == null || algorithm.isBlank()) {
      throw new IllegalArgumentException("algorithm is required");
    }
    if (timestamp == null || timestamp.isBlank()) {
      throw new IllegalArgumentException("timestamp is required");
    }
    name = name == null ? "" : name;
    if (name.getBytes(StandardCharsets.UTF_8).length > MAX_METADATA_STRING_BYTES) {
      throw new IllegalArgumentException(
          "name must be at most " + MAX_METADATA_STRING_BYTES + " bytes");
    }
  }

  /**
   * Build a proof for some content, fingerprinted now (the timestamp is supplied for testability).
   */
  public static ProofRecord forContent(byte[] content, String name, Instant when) {
    return new ProofRecord(
        DocumentFingerprint.of(content).hex(),
        DocumentFingerprint.ALGORITHM,
        when.toString(),
        name);
  }
}
