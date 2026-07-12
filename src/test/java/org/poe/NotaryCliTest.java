package org.poe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
        .contains("verify")
        .contains("submit");
    assertThat(NotaryCli.run(new String[] {"bogus"})).contains("usage:");
  }

  @Test
  @DisplayName("submit records the proof via the submitter and reports the tx hash + flags")
  void submitCommand() throws IOException {
    Path f = fileWith("notarize me");
    String expected = DocumentFingerprint.of("notarize me".getBytes(StandardCharsets.UTF_8)).hex();
    AtomicReference<ProofRecord> proof = new AtomicReference<>();
    AtomicReference<Map<String, String>> flags = new AtomicReference<>();
    NotaryCli.ProofSubmitter fake =
        (p, fl) -> {
          proof.set(p);
          flags.set(fl);
          return "tx_abc123";
        };

    String out =
        NotaryCli.run(
            new String[] {"submit", f.toString(), "contract", "--network", "preview"}, fake);

    assertThat(out).contains("tx_abc123").contains("hash=" + expected).contains("name=contract");
    assertThat(proof.get().hashHex()).isEqualTo(expected);
    assertThat(proof.get().algorithm()).isEqualTo("SHA-256");
    assertThat(proof.get().name()).isEqualTo("contract");
    assertThat(flags.get()).containsEntry("network", "preview");
  }

  @Test
  @DisplayName("submit --description records it and reports it")
  void submitWithDescription() throws IOException {
    Path f = fileWith("doc");
    AtomicReference<ProofRecord> proof = new AtomicReference<>();
    NotaryCli.ProofSubmitter fake =
        (p, fl) -> {
          proof.set(p);
          return "tx_x";
        };
    String out =
        NotaryCli.run(
            new String[] {"submit", f.toString(), "label", "--description", "final signed copy"},
            fake);
    assertThat(proof.get().description()).isEqualTo("final signed copy");
    assertThat(out).contains("description=final signed copy");
  }

  @Test
  @DisplayName("submit with no file prints usage (does not call the submitter)")
  void submitNoFile() {
    NotaryCli.ProofSubmitter mustNotRun =
        (p, fl) -> {
          throw new AssertionError("submitter must not run without a file");
        };
    assertThat(NotaryCli.run(new String[] {"submit"}, mustNotRun)).contains("usage:");
  }

  @Test
  @DisplayName("submit reports a failure cleanly, without a stack trace")
  void submitFailure() throws IOException {
    Path f = fileWith("x");
    NotaryCli.ProofSubmitter boom =
        (p, fl) -> {
          throw new RuntimeException("backend down");
        };
    assertThat(NotaryCli.run(new String[] {"submit", f.toString()}, boom))
        .isEqualTo("submit failed: backend down");
  }

  @Test
  @DisplayName("resolveConfig requires a key (signing key or mnemonic)")
  void resolveConfigRequiresKey() {
    assertThatThrownBy(() -> NotaryCli.resolveConfig(Map.of(), Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("--signing-key")
        .hasMessageContaining("POE_NOTARY_MNEMONIC");
  }

  @Test
  @DisplayName("resolveConfig: a signing key needs a sender address")
  void resolveConfigSigningKeyNeedsAddress() {
    assertThatThrownBy(() -> NotaryCli.resolveConfig(Map.of("signing-key", "/tmp/k.sk"), Map.of()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("address");
  }

  @Test
  @DisplayName("resolveConfig: blockfrost backend needs a project id")
  void resolveConfigBlockfrostNeedsProjectId() {
    assertThatThrownBy(
            () ->
                NotaryCli.resolveConfig(
                    Map.of("backend", "blockfrost"), Map.of("POE_NOTARY_MNEMONIC", "m")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("blockfrost");
  }

  @Test
  @DisplayName("resolveConfig: flags win over env, defaults fill the rest (koios keyless)")
  void resolveConfigPrecedenceAndDefaults() {
    NotaryCli.SubmitConfig cfg =
        NotaryCli.resolveConfig(
            Map.of("network", "mainnet"), // flag wins over env below
            Map.of("POE_NETWORK", "preprod", "POE_NOTARY_MNEMONIC", "seed"));
    assertThat(cfg.network()).isEqualTo(Networks.mainnet());
    assertThat(cfg.backend()).isEqualTo(NotaryCli.BackendKind.KOIOS);
    assertThat(cfg.backendUrl()).isEqualTo("https://api.koios.rest/api/v1/"); // default, mainnet
    assertThat(cfg.mnemonic()).isEqualTo("seed");
    assertThat(cfg.signingKeyFile()).isNull();
  }

  @Test
  @DisplayName("resolveConfig: signing key + address, blockfrost with project id")
  void resolveConfigSigningKeyAndBlockfrost() {
    NotaryCli.SubmitConfig cfg =
        NotaryCli.resolveConfig(
            Map.of(
                "signing-key", "/keys/my.sk",
                "address", "addr_test1vxyz",
                "backend", "blockfrost",
                "blockfrost-project-id", "preprodABC",
                "network", "preprod"),
            Map.of());
    assertThat(cfg.signingKeyFile()).isEqualTo(Path.of("/keys/my.sk"));
    assertThat(cfg.senderAddress()).isEqualTo("addr_test1vxyz");
    assertThat(cfg.backend()).isEqualTo(NotaryCli.BackendKind.BLOCKFROST);
    assertThat(cfg.blockfrostProjectId()).isEqualTo("preprodABC");
    assertThat(cfg.backendUrl()).isEqualTo("https://cardano-preprod.blockfrost.io/api/v0/");
  }

  @Test
  @DisplayName("defaultBackendUrl covers koios + blockfrost + yaci")
  void defaultBackendUrls() {
    assertThat(NotaryCli.defaultBackendUrl(NotaryCli.BackendKind.KOIOS, "preview"))
        .isEqualTo("https://preview.koios.rest/api/v1/");
    assertThat(NotaryCli.defaultBackendUrl(NotaryCli.BackendKind.KOIOS, "preprod"))
        .isEqualTo("https://preprod.koios.rest/api/v1/");
    assertThat(NotaryCli.defaultBackendUrl(NotaryCli.BackendKind.BLOCKFROST, "mainnet"))
        .isEqualTo("https://cardano-mainnet.blockfrost.io/api/v0/");
    assertThat(NotaryCli.defaultBackendUrl(NotaryCli.BackendKind.YACI, "preprod"))
        .isEqualTo("http://localhost:8080/api/v1/");
  }

  @Test
  @DisplayName("yaci backend needs no project id and defaults to a local URL")
  void resolveConfigYaciLocalNoKeyless() {
    NotaryCli.SubmitConfig cfg =
        NotaryCli.resolveConfig(
            Map.of("backend", "yaci", "network", "testnet"),
            Map.of("POE_NOTARY_MNEMONIC", "seed words"));
    assertThat(cfg.backend()).isEqualTo(NotaryCli.BackendKind.YACI);
    assertThat(cfg.backendUrl()).isEqualTo("http://localhost:8080/api/v1/");
    assertThat(NotaryCli.backendFor(cfg)).isInstanceOf(BFBackendService.class);
  }

  @Test
  @DisplayName("networkFor maps known names and defaults unknown/absent to testnet")
  void networkForMapping() {
    assertThat(NotaryCli.networkFor(null)).isEqualTo(Networks.testnet());
    assertThat(NotaryCli.networkFor("weird")).isEqualTo(Networks.testnet());
    assertThat(NotaryCli.networkFor("mainnet")).isEqualTo(Networks.mainnet());
    assertThat(NotaryCli.networkFor("preview")).isEqualTo(Networks.preview());
    assertThat(NotaryCli.networkFor("preprod")).isEqualTo(Networks.preprod());
  }

  @Test
  @DisplayName("backendFor builds the selected provider implementation")
  void backendForSelectsImpl() {
    NotaryCli.SubmitConfig koios =
        NotaryCli.resolveConfig(Map.of("network", "preprod"), Map.of("POE_NOTARY_MNEMONIC", "m"));
    assertThat(NotaryCli.backendFor(koios)).isInstanceOf(KoiosBackendService.class);

    NotaryCli.SubmitConfig bf =
        NotaryCli.resolveConfig(
            Map.of("backend", "blockfrost", "blockfrost-project-id", "id", "network", "preprod"),
            Map.of("POE_NOTARY_MNEMONIC", "m"));
    assertThat(NotaryCli.backendFor(bf)).isInstanceOf(BFBackendService.class);
  }

  @Test
  @DisplayName("senderFor derives the address + a signer from a mnemonic")
  void senderForFromMnemonic() {
    Account account = new Account(Networks.preprod()); // fresh random mnemonic
    NotaryCli.SubmitConfig cfg =
        NotaryCli.resolveConfig(
            Map.of("network", "preprod"), Map.of("POE_NOTARY_MNEMONIC", account.mnemonic()));
    NotaryCli.Sender sender = NotaryCli.senderFor(cfg);
    assertThat(sender.address()).isEqualTo(account.baseAddress());
    assertThat(sender.signer()).isNotNull();
  }

  @Test
  @DisplayName("loadSecretKey reads a cardano-cli signing key; senderFor uses it + the address")
  void senderForFromSigningKey() throws IOException {
    String cborHex = "5820" + "ab".repeat(32);
    Path key = tmp.resolve("my.sk");
    Files.writeString(
        key,
        "{\n  \"type\": \"PaymentSigningKeyShelley_ed25519\",\n"
            + "  \"description\": \"Payment Signing Key\",\n"
            + "  \"cborHex\": \""
            + cborHex
            + "\"\n}\n");

    assertThat(NotaryCli.loadSecretKey(key).getCborHex()).isEqualTo(cborHex);

    NotaryCli.SubmitConfig cfg =
        NotaryCli.resolveConfig(
            Map.of(
                "signing-key", key.toString(), "address", "addr_test1vabc", "network", "preprod"),
            Map.of());
    NotaryCli.Sender sender = NotaryCli.senderFor(cfg);
    assertThat(sender.address()).isEqualTo("addr_test1vabc");
    assertThat(sender.signer()).isNotNull();
  }
}
