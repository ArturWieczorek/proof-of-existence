package org.poe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Document fingerprint (SHA-256)")
class DocumentFingerprintTest {

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("matches the standard SHA-256 known-answer vectors")
  void knownAnswers() {
    // These are the published SHA-256 test vectors - if our hashing is correct, they must match.
    assertThat(DocumentFingerprint.of(bytes("")).hex())
        .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    assertThat(DocumentFingerprint.of(bytes("abc")).hex())
        .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
  }

  @Test
  @DisplayName("is deterministic and 32 bytes / 64 hex chars")
  void deterministicAndSized() {
    DocumentFingerprint a = DocumentFingerprint.of(bytes("hello cardano"));
    DocumentFingerprint b = DocumentFingerprint.of(bytes("hello cardano"));
    assertThat(a.hex()).isEqualTo(b.hex());
    assertThat(a.hash()).hasSize(32);
    assertThat(a.hex()).hasSize(64);
  }

  @Test
  @DisplayName("different content yields a different fingerprint")
  void collisionAvoidance() {
    assertThat(DocumentFingerprint.of(bytes("document v1")).hex())
        .isNotEqualTo(DocumentFingerprint.of(bytes("document v2")).hex());
  }

  @Test
  @DisplayName("matches() recognises the original and rejects a tampered copy")
  void matchesOriginalOnly() {
    byte[] original = bytes("the original contract text");
    DocumentFingerprint fp = DocumentFingerprint.of(original);
    assertThat(fp.matches(original)).isTrue();
    assertThat(fp.matches(bytes("the original contract text."))).isFalse(); // one extra char
  }
}
