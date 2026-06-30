package org.poe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Verifier")
class VerifierTest {

  private static byte[] doc(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("accepts the original document and rejects a tampered one")
  void verifyMatch() {
    byte[] original = doc("the agreement, version 1");
    ProofRecord proof =
        ProofRecord.forContent(original, "agreement", Instant.parse("2026-06-30T00:00:00Z"));

    assertThat(Verifier.verify(original, proof)).isTrue();
    assertThat(Verifier.verify(doc("the agreement, version 2"), proof)).isFalse();
  }

  @Test
  @DisplayName("end to end: record -> metadata -> parse back -> verify")
  void roundTripThroughMetadata() {
    byte[] original = doc("notarize me");
    ProofRecord proof =
        ProofRecord.forContent(original, "demo", Instant.parse("2026-06-30T00:00:00Z"));

    // Simulate writing to chain and reading back: build the metadata map, then parse it.
    ProofRecord readBack = Notary.parseProof(Notary.proofMap(proof));

    assertThat(readBack).isEqualTo(proof);
    assertThat(Verifier.verify(original, readBack)).isTrue();
    assertThat(Verifier.verify(doc("notarize me!"), readBack)).isFalse();
  }

  @Test
  @DisplayName("refuses to verify a proof made with an unknown algorithm")
  void unknownAlgorithm() {
    String hash = DocumentFingerprint.of(doc("x")).hex();
    ProofRecord blake = new ProofRecord(hash, "blake2b-256", "2026-06-30T00:00:00Z", "");
    assertThatThrownBy(() -> Verifier.verify(doc("x"), blake))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
