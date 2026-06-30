package org.poe;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Notary CLI")
class NotaryCliTest {

  @TempDir Path tmp;

  private Path fileWith(String content) throws IOException {
    Path f = tmp.resolve("doc.txt");
    Files.writeString(f, content);
    return f;
  }

  @Test
  @DisplayName("hash prints the file's SHA-256 fingerprint")
  void hashCommand() throws IOException {
    Path f = fileWith("hello");
    String expected = DocumentFingerprint.of("hello".getBytes(StandardCharsets.UTF_8)).hex();
    assertThat(NotaryCli.run(new String[] {"hash", f.toString()})).isEqualTo(expected);
  }

  @Test
  @DisplayName("verify reports MATCH for the right hash and NO MATCH otherwise")
  void verifyCommand() throws IOException {
    Path f = fileWith("hello");
    String good = DocumentFingerprint.of("hello".getBytes(StandardCharsets.UTF_8)).hex();
    assertThat(NotaryCli.run(new String[] {"verify", f.toString(), good})).isEqualTo("MATCH");
    assertThat(NotaryCli.run(new String[] {"verify", f.toString(), "00".repeat(32)}))
        .isEqualTo("NO MATCH");
  }

  @Test
  @DisplayName("no args or an unknown command prints usage")
  void usage() {
    assertThat(NotaryCli.run(new String[] {}))
        .contains("usage:")
        .contains("hash")
        .contains("verify");
    assertThat(NotaryCli.run(new String[] {"bogus"})).contains("usage:");
  }
}
