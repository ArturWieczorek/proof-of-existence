package org.poe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * The fingerprint of a document: its SHA-256 hash. This is the only thing that ever goes on-chain -
 * the document itself stays private.
 *
 * <p>We use SHA-256 from the JDK: it is universal, has well-known test vectors, and needs no extra
 * dependency. (Cardano's native hash is blake2b-256; we note it as an alternative, but for an
 * off-chain notary SHA-256 is the pragmatic, portable choice.)
 */
public record DocumentFingerprint(byte[] hash) {

  public static final String ALGORITHM = "SHA-256";

  /** Fingerprint of the given content. */
  public static DocumentFingerprint of(byte[] content) {
    try {
      return new DocumentFingerprint(MessageDigest.getInstance(ALGORITHM).digest(content));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 must be available", e);
    }
  }

  /** Fingerprint of a file's contents. */
  public static DocumentFingerprint ofFile(Path file) {
    try {
      return of(Files.readAllBytes(file));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read file: " + file, e);
    }
  }

  /** The fingerprint as lowercase hex (64 characters for SHA-256). */
  public String hex() {
    return HexFormat.of().formatHex(hash);
  }

  /**
   * True if {@code content} hashes to this same fingerprint (i.e. it is the notarized document).
   */
  public boolean matches(byte[] content) {
    return Arrays.equals(hash, of(content).hash);
  }
}
