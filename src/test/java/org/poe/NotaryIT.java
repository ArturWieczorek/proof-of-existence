package org.poe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.quicktx.TxResult;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Integration: actually record a proof on a devnet/testnet. Self-skips unless a funded notary
 * account and a backend URL are provided:
 *
 * <pre>
 *   export POE_NOTARY_MNEMONIC="...funded mnemonic..."
 *   export POE_BACKEND_URL="http://localhost:8080/api/v1/"   # Yaci DevKit, or a testnet Blockfrost URL
 *   ./gradlew integrationTest
 * </pre>
 */
@Tag("integration")
@DisplayName("Notary (on-chain)")
class NotaryIT {

  @Test
  @DisplayName("records a proof and returns a transaction hash")
  void recordsOnChain() {
    String mnemonic = System.getenv("POE_NOTARY_MNEMONIC");
    String backendUrl = System.getenv("POE_BACKEND_URL");
    assumeTrue(
        mnemonic != null && backendUrl != null,
        "set POE_NOTARY_MNEMONIC and POE_BACKEND_URL to run this test");

    BackendService backend = new BFBackendService(backendUrl, "devnet");
    Account notary = new Account(Networks.testnet(), mnemonic);

    ProofRecord proof =
        ProofRecord.forContent(
            "hello on-chain".getBytes(StandardCharsets.UTF_8), "it-demo", Instant.now());

    TxResult result = Notary.record(backend, notary, proof);
    assertThat(result.getTxHash()).isNotBlank();
  }
}
