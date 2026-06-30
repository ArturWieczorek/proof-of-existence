package org.poe;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.api.model.Amount;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import com.bloxbean.cardano.client.quicktx.QuickTxBuilder;
import com.bloxbean.cardano.client.quicktx.Tx;
import com.bloxbean.cardano.client.quicktx.TxResult;
import java.math.BigInteger;

/**
 * Records a {@link ProofRecord} on Cardano as transaction metadata - the simplest possible on-chain
 * write (no smart contract needed). The proof rides in a normal transaction under a fixed metadata
 * label; anyone can read it back later to verify a document (Chapter 03).
 */
public final class Notary {

  private Notary() {}

  /**
   * Our metadata label. Labels namespace metadata on-chain; we pick a fixed number for this notary.
   * (There is no global registry for proof-of-existence labels; document yours.)
   */
  public static final long METADATA_LABEL = 1718;

  /** The inner metadata map for a proof: {@code {h, alg, ts, name}}. */
  public static CBORMetadataMap proofMap(ProofRecord proof) {
    CBORMetadataMap map = new CBORMetadataMap();
    map.put("h", proof.hashHex());
    map.put("alg", proof.algorithm());
    map.put("ts", proof.timestamp());
    map.put("name", proof.name());
    return map;
  }

  /** The full transaction metadata for a proof, under {@link #METADATA_LABEL}. */
  public static Metadata buildMetadata(ProofRecord proof) {
    CBORMetadata metadata = new CBORMetadata();
    metadata.put(BigInteger.valueOf(METADATA_LABEL), proofMap(proof));
    return metadata;
  }

  /**
   * Read a {@link ProofRecord} back out of a proof metadata map (the inverse of {@link #proofMap}).
   * This is what you do after fetching a transaction's metadata from the chain, before verifying.
   */
  public static ProofRecord parseProof(CBORMetadataMap map) {
    return new ProofRecord(
        String.valueOf(map.get("h")),
        String.valueOf(map.get("alg")),
        String.valueOf(map.get("ts")),
        String.valueOf(map.get("name")));
  }

  /**
   * Submit a proof to the chain: a tiny self-payment carrying the proof metadata. The notary
   * account pays the fee and signs. (Integration - needs a live backend; exercised from the
   * devnet/testnet.)
   */
  public static TxResult record(BackendService backend, Account notary, ProofRecord proof) {
    Tx tx =
        new Tx()
            .payToAddress(notary.baseAddress(), Amount.ada(1))
            .attachMetadata(buildMetadata(proof))
            .from(notary.baseAddress());
    return new QuickTxBuilder(backend)
        .compose(tx)
        .withSigner(SignerProviders.signerFrom(notary))
        .complete();
  }
}
