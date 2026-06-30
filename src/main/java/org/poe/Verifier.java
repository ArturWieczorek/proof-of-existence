package org.poe;

/**
 * Verifies a document against a recorded proof: re-hash the document you hold and check it matches
 * the hash that was recorded on-chain. If it matches, the document is exactly the one that was
 * notarized at the proof's timestamp.
 */
public final class Verifier {

  private Verifier() {}

  /**
   * @return true if {@code content} reproduces the recorded fingerprint.
   * @throws UnsupportedOperationException if the proof used a hash algorithm this tool cannot
   *     compute
   */
  public static boolean verify(byte[] content, ProofRecord recorded) {
    if (!DocumentFingerprint.ALGORITHM.equals(recorded.algorithm())) {
      throw new UnsupportedOperationException(
          "cannot verify a proof made with algorithm: " + recorded.algorithm());
    }
    return DocumentFingerprint.of(content).hex().equals(recorded.hashHex());
  }
}
