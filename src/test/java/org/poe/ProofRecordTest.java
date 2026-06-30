package org.poe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Proof record")
class ProofRecordTest {

  @Test
  @DisplayName("forContent fingerprints the content and records algorithm + timestamp")
  void forContent() {
    Instant when = Instant.parse("2026-06-30T12:00:00Z");
    ProofRecord p =
        ProofRecord.forContent("hello".getBytes(StandardCharsets.UTF_8), "greeting", when);

    assertThat(p.hashHex()).isEqualTo(DocumentFingerprint.of("hello".getBytes()).hex());
    assertThat(p.algorithm()).isEqualTo("SHA-256");
    assertThat(p.timestamp()).isEqualTo("2026-06-30T12:00:00Z");
    assertThat(p.name()).isEqualTo("greeting");
  }

  @Test
  @DisplayName("rejects a malformed hash")
  void rejectsBadHash() {
    assertThatThrownBy(() -> new ProofRecord("not-hex", "SHA-256", "2026-06-30T12:00:00Z", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("rejects a name longer than the metadata string limit")
  void rejectsLongName() {
    String hash = DocumentFingerprint.of("x".getBytes()).hex();
    String tooLong = "x".repeat(65);
    assertThatThrownBy(() -> new ProofRecord(hash, "SHA-256", "2026-06-30T12:00:00Z", tooLong))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("treats a null name as empty")
  void nullNameBecomesEmpty() {
    String hash = DocumentFingerprint.of("x".getBytes()).hex();
    assertThat(new ProofRecord(hash, "SHA-256", "2026-06-30T12:00:00Z", null).name()).isEmpty();
  }
}
