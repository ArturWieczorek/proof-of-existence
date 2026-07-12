package org.poe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Notary metadata")
class NotaryTest {

  private static ProofRecord sampleProof() {
    return new ProofRecord(
        DocumentFingerprint.of("contract".getBytes()).hex(),
        "SHA-256",
        "2026-06-30T12:00:00Z",
        "my-contract");
  }

  @Test
  @DisplayName("the proof map carries hash, algorithm, timestamp and name")
  void proofMapFields() {
    ProofRecord p = sampleProof();
    var map = Notary.proofMap(p);
    assertThat(map.get("h").toString()).isEqualTo(p.hashHex());
    assertThat(map.get("alg").toString()).isEqualTo("SHA-256");
    assertThat(map.get("ts").toString()).isEqualTo("2026-06-30T12:00:00Z");
    assertThat(map.get("name").toString()).isEqualTo("my-contract");
  }

  @Test
  @DisplayName("description is written only when set, and parseProof round-trips it")
  void descriptionRoundTrip() {
    // absent when empty -> proofs without a description keep the old shape; parseProof yields ""
    var noDesc = Notary.proofMap(sampleProof());
    assertThat(noDesc.get("description")).isNull();
    assertThat(Notary.parseProof(noDesc).description()).isEmpty();

    // present when set, and round-trips through parse
    ProofRecord withDesc =
        new ProofRecord(
            DocumentFingerprint.of("contract".getBytes()).hex(),
            "SHA-256",
            "2026-06-30T12:00:00Z",
            "my-contract",
            "final signed copy");
    var map = Notary.proofMap(withDesc);
    assertThat(map.get("description").toString()).isEqualTo("final signed copy");
    ProofRecord parsed = Notary.parseProof(map);
    assertThat(parsed.name()).isEqualTo("my-contract");
    assertThat(parsed.description()).isEqualTo("final signed copy");
  }

  @Test
  @DisplayName("the serialized metadata embeds the proof under our label")
  void metadataSerializes() throws Exception {
    ProofRecord p = sampleProof();
    byte[] cbor = Notary.buildMetadata(p).serialize();
    // The text values appear verbatim in the CBOR bytes - a robust, encoding-agnostic check.
    String asText = new String(cbor, StandardCharsets.ISO_8859_1);
    assertThat(cbor).isNotEmpty();
    assertThat(asText).contains(p.hashHex()).contains("SHA-256").contains("my-contract");
  }
}
