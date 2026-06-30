package org.poe;

import com.bloxbean.cardano.client.metadata.Metadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadata;
import com.bloxbean.cardano.client.metadata.cbor.CBORMetadataMap;
import java.math.BigInteger;

/**
 * Builds the CIP-25 metadata for an optional NFT "certificate" of a notarization. Minting a token
 * with this metadata gives the document owner a portable, wallet-visible certificate that embeds
 * the proof (hash, algorithm, timestamp).
 *
 * <p>CIP-25 is the standard NFT-metadata format (label 721), shaped as: {@code 721 -> { policyId ->
 * { assetName -> { ...fields } } }}. Wallets and explorers render it. Here we only build the
 * metadata; minting the token (a native-script policy + submit) is an integration step described in
 * the chapter.
 */
public final class Certificate {

  private Certificate() {}

  /** The CIP-25 metadata label. */
  public static final long CIP25_LABEL = 721;

  /** Build CIP-25 metadata for a certificate token of the given proof. */
  public static Metadata cip25(String policyIdHex, String assetName, ProofRecord proof) {
    CBORMetadataMap fields = new CBORMetadataMap();
    fields.put("name", "Proof of Existence Certificate");
    fields.put("proofHash", proof.hashHex());
    fields.put("alg", proof.algorithm());
    fields.put("ts", proof.timestamp());
    if (!proof.name().isEmpty()) {
      fields.put("doc", proof.name());
    }

    CBORMetadataMap assetMap = new CBORMetadataMap();
    assetMap.put(assetName, fields);

    CBORMetadataMap policyMap = new CBORMetadataMap();
    policyMap.put(policyIdHex, assetMap);

    CBORMetadata metadata = new CBORMetadata();
    metadata.put(BigInteger.valueOf(CIP25_LABEL), policyMap);
    return metadata;
  }
}
