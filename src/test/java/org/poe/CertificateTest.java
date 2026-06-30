package org.poe;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CIP-25 certificate metadata")
class CertificateTest {

  @Test
  @DisplayName("embeds the proof under a CIP-25 (721) structure")
  void buildsCip25() throws Exception {
    ProofRecord proof =
        new ProofRecord(
            DocumentFingerprint.of("deed".getBytes()).hex(),
            "SHA-256",
            "2026-06-30T00:00:00Z",
            "title-deed");

    byte[] cbor =
        Certificate.cip25(
                "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8", "poe-cert", proof)
            .serialize();

    String asText = new String(cbor, StandardCharsets.ISO_8859_1);
    assertThat(cbor).isNotEmpty();
    assertThat(asText)
        .contains(proof.hashHex())
        .contains("Proof of Existence Certificate")
        .contains("poe-cert")
        .contains("title-deed");
  }
}
