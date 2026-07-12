package org.poe;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;

/**
 * The data we record on-chain for one notarization: the document's hash, the algorithm, a
 * timestamp, an optional short name/label, and an optional short description. Deliberately tiny -
 * only the hash, never the document. The name/description are annotations, NOT part of the proof
 * (the proof is the hash + the block time); they just make the record readable.
 *
 * @param hashHex 64 lowercase hex chars (a SHA-256 digest)
 * @param algorithm the hash algorithm name (e.g. "SHA-256")
 * @param timestamp ISO-8601 instant string (UTC), e.g. "2026-06-30T12:00:00Z"
 * @param name an optional short human label (empty if none); max 64 bytes (Cardano metadata limit)
 * @param description an optional short human description (empty if none); max 64 bytes
 */
public record ProofRecord(
    String hashHex, String algorithm, String timestamp, String name, String description) {

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
    description = description == null ? "" : description;
    if (description.getBytes(StandardCharsets.UTF_8).length > MAX_METADATA_STRING_BYTES) {
      throw new IllegalArgumentException(
          "description must be at most " + MAX_METADATA_STRING_BYTES + " bytes");
    }
  }

  /** Backward-compatible constructor: a proof with no description. */
  public ProofRecord(String hashHex, String algorithm, String timestamp, String name) {
    this(hashHex, algorithm, timestamp, name, "");
  }

  /**
   * Build a proof for some content, fingerprinted now (the timestamp is supplied for testability).
   */
  public static ProofRecord forContent(byte[] content, String name, Instant when) {
    return forContent(content, name, "", when);
  }

  /** Build a proof for some content, with a description. */
  public static ProofRecord forContent(
      byte[] content, String name, String description, Instant when) {
    return new ProofRecord(
        DocumentFingerprint.of(content).hex(),
        DocumentFingerprint.ALGORITHM,
        when.toString(),
        name,
        description);
  }
}
