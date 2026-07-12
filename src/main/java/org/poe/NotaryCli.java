package org.poe;

import com.bloxbean.cardano.client.account.Account;
import com.bloxbean.cardano.client.backend.api.BackendService;
import com.bloxbean.cardano.client.backend.blockfrost.service.BFBackendService;
import com.bloxbean.cardano.client.backend.koios.KoiosBackendService;
import com.bloxbean.cardano.client.common.model.Network;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.function.TxSigner;
import com.bloxbean.cardano.client.function.helper.SignerProviders;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny command-line front end for the notary, so it is a usable tool, not just a library.
 *
 * <pre>
 *   notary hash &lt;file&gt;              print the document's SHA-256 fingerprint
 *   notary verify &lt;file&gt; &lt;hashHex&gt;  check a file against a recorded fingerprint -> MATCH / NO MATCH
 *   notary proof &lt;file&gt; [name]      print the proof record you would record on-chain
 *   notary submit &lt;file&gt; [name]     record the proof on-chain -> transaction hash
 * </pre>
 *
 * <p>Only {@code submit} touches the network + a key. Everything is configuration (flags win, then
 * environment variables, then defaults), so the same binary works keyless out of the box and scales
 * devnet -> testnet -> mainnet without a rebuild:
 *
 * <pre>
 *   --network preprod|preview|mainnet|testnet   POE_NETWORK             (default: preprod)
 *   --backend koios|blockfrost|yaci             POE_BACKEND             (default: koios, keyless)
 *   --backend-url &lt;url&gt;                          POE_BACKEND_URL         (default: per backend+network)
 *   --blockfrost-project-id &lt;id&gt;                 POE_BLOCKFROST_PROJECT_ID (required for --backend blockfrost)
 *   --signing-key &lt;file.sk&gt;                      POE_SIGNING_KEY         (a cardano-cli signing key)
 *   --address &lt;addr&gt;                             POE_NOTARY_ADDRESS      (required with --signing-key)
 *                                               POE_NOTARY_MNEMONIC     (a mnemonic; env only, it is a secret)
 * </pre>
 *
 * Provide the key as EITHER a cardano-cli {@code --signing-key} (with {@code --address}) OR a
 * mnemonic in {@code POE_NOTARY_MNEMONIC}. Secrets never go in flags (they leak into shell
 * history).
 */
public final class NotaryCli {

  private NotaryCli() {}

  /** Records a proof and returns its transaction hash. The seam we mock in unit tests. */
  @FunctionalInterface
  interface ProofSubmitter {
    String submit(ProofRecord proof, Map<String, String> flags) throws Exception;
  }

  /** Which provider to talk to: keyless Koios, keyed Blockfrost, or a local Yaci DevKit. */
  enum BackendKind {
    KOIOS,
    BLOCKFROST,
    YACI
  }

  /** Resolved submit configuration (network + provider + key are all config, never hardcoded). */
  record SubmitConfig(
      Network network,
      BackendKind backend,
      String backendUrl,
      String blockfrostProjectId,
      String mnemonic,
      Path signingKeyFile,
      String senderAddress) {}

  /** A resolved sender: the address that pays, and the signer that authorizes. */
  record Sender(String address, TxSigner signer) {}

  private static final Pattern CBOR_HEX = Pattern.compile("\"cborHex\"\\s*:\\s*\"([0-9a-fA-F]+)\"");

  public static void main(String[] args) {
    System.out.println(run(args));
  }

  /**
   * Public entry point: uses the real on-chain submitter (reads config from flags + environment).
   */
  public static String run(String[] args) {
    return run(args, defaultSubmitter());
  }

  /**
   * Testable entry point: the on-chain write goes through {@code submitter}, so unit tests can
   * drive the {@code submit} command without a backend, a key, or the network.
   */
  static String run(String[] args, ProofSubmitter submitter) {
    if (args.length == 0) {
      return usage();
    }
    return switch (args[0]) {
      case "hash" -> args.length < 2 ? usage() : DocumentFingerprint.ofFile(Path.of(args[1])).hex();
      case "verify" ->
          args.length < 3
              ? usage()
              : (DocumentFingerprint.ofFile(Path.of(args[1])).hex().equals(args[2])
                  ? "MATCH"
                  : "NO MATCH");
      case "proof" -> {
        if (args.length < 2) {
          yield usage();
        }
        ProofRecord p = proofFor(args[1], args.length > 2 ? args[2] : "");
        yield "hash=" + p.hashHex() + " alg=" + p.algorithm() + " name=" + p.name();
      }
      case "submit" -> {
        List<String> positionals = new ArrayList<>();
        Map<String, String> flags = parseFlags(args, 1, positionals);
        if (positionals.isEmpty()) {
          yield usage();
        }
        String name = positionals.size() > 1 ? positionals.get(1) : flags.getOrDefault("name", "");
        ProofRecord p = proofFor(positionals.get(0), name);
        try {
          String txHash = submitter.submit(p, flags);
          yield "submitted: tx=" + txHash + " hash=" + p.hashHex() + " name=" + p.name();
        } catch (Exception e) {
          yield "submit failed: " + e.getMessage();
        }
      }
      default -> usage();
    };
  }

  private static ProofRecord proofFor(String file, String name) {
    return new ProofRecord(
        DocumentFingerprint.ofFile(Path.of(file)).hex(),
        DocumentFingerprint.ALGORITHM,
        Instant.now().toString(),
        name);
  }

  /** The real submitter: resolve config, build a backend + signer, and record the proof. */
  private static ProofSubmitter defaultSubmitter() {
    return (proof, flags) -> {
      SubmitConfig cfg = resolveConfig(flags, System.getenv());
      Sender sender = senderFor(cfg);
      return Notary.record(backendFor(cfg), sender.address(), sender.signer(), proof).getTxHash();
    };
  }

  /**
   * Resolve flags + environment into a {@link SubmitConfig} (flags win, then env, then defaults),
   * or throw a clear, actionable error. Pure (takes both maps) so it is fully unit-testable
   * offline.
   */
  static SubmitConfig resolveConfig(Map<String, String> flags, Map<String, String> env) {
    String networkName = pick(flags, "network", env, "POE_NETWORK", "preprod");
    Network network = networkFor(networkName);
    BackendKind backend =
        switch (pick(flags, "backend", env, "POE_BACKEND", "koios").trim().toLowerCase()) {
          case "blockfrost" -> BackendKind.BLOCKFROST;
          case "yaci" -> BackendKind.YACI;
          default -> BackendKind.KOIOS;
        };
    String projectId = pick(flags, "blockfrost-project-id", env, "POE_BLOCKFROST_PROJECT_ID", null);
    String backendUrl =
        pick(flags, "backend-url", env, "POE_BACKEND_URL", defaultBackendUrl(backend, networkName));
    String mnemonic = blankToNull(env.get("POE_NOTARY_MNEMONIC")); // secret: env only, never a flag
    String signingKey = pick(flags, "signing-key", env, "POE_SIGNING_KEY", null);
    String address = pick(flags, "address", env, "POE_NOTARY_ADDRESS", null);

    if (backend == BackendKind.BLOCKFROST && isBlank(projectId)) {
      throw new IllegalStateException(
          "blockfrost backend needs --blockfrost-project-id (or POE_BLOCKFROST_PROJECT_ID)");
    }
    Path keyFile = null;
    if (!isBlank(signingKey)) {
      keyFile = Path.of(signingKey);
      if (isBlank(address)) {
        throw new IllegalStateException(
            "a signing key needs its sender address: --address (or POE_NOTARY_ADDRESS)");
      }
    } else if (isBlank(mnemonic)) {
      throw new IllegalStateException(
          "provide a key: --signing-key <file.sk> (with --address) or set POE_NOTARY_MNEMONIC");
    }
    return new SubmitConfig(network, backend, backendUrl, projectId, mnemonic, keyFile, address);
  }

  /**
   * Build the backend implementation the config selects (all implement {@link BackendService}).
   * Yaci DevKit speaks the Blockfrost API over a local node, so it reuses {@link BFBackendService}
   * with a placeholder project id (a local devnet needs no real key).
   */
  static BackendService backendFor(SubmitConfig cfg) {
    return switch (cfg.backend()) {
      case BLOCKFROST -> new BFBackendService(cfg.backendUrl(), cfg.blockfrostProjectId());
      case YACI ->
          new BFBackendService(
              cfg.backendUrl(),
              cfg.blockfrostProjectId() == null ? "yaci" : cfg.blockfrostProjectId());
      case KOIOS -> new KoiosBackendService(cfg.backendUrl());
    };
  }

  /** Resolve the sender address + signer from either a cardano-cli key or a mnemonic. */
  static Sender senderFor(SubmitConfig cfg) {
    if (cfg.signingKeyFile() != null) {
      SecretKey key = loadSecretKey(cfg.signingKeyFile());
      return new Sender(cfg.senderAddress(), SignerProviders.signerFrom(key));
    }
    Account account = new Account(cfg.network(), cfg.mnemonic());
    return new Sender(account.baseAddress(), SignerProviders.signerFrom(account));
  }

  /** Load a cardano-cli signing key file (its JSON {@code cborHex}) into a bloxbean SecretKey. */
  static SecretKey loadSecretKey(Path path) {
    try {
      Matcher m = CBOR_HEX.matcher(Files.readString(path));
      if (!m.find()) {
        throw new IllegalStateException("not a cardano-cli signing key (no cborHex): " + path);
      }
      return new SecretKey(m.group(1));
    } catch (IOException e) {
      throw new UncheckedIOException("cannot read signing key: " + path, e);
    }
  }

  /** Default provider URL for a backend + network, so it works with no --backend-url. */
  static String defaultBackendUrl(BackendKind kind, String networkName) {
    String n = networkName == null ? "preprod" : networkName.trim().toLowerCase();
    // Yaci DevKit runs a local node + Blockfrost-compatible API; its default endpoint is local.
    if (kind == BackendKind.YACI) {
      return "http://localhost:8080/api/v1/";
    }
    if (kind == BackendKind.KOIOS) {
      // Trailing slash is required (the Koios client builds a Retrofit base URL).
      return switch (n) {
        case "mainnet" -> "https://api.koios.rest/api/v1/";
        case "preview" -> "https://preview.koios.rest/api/v1/";
        default -> "https://preprod.koios.rest/api/v1/"; // preprod / legacy testnet
      };
    }
    return switch (n) {
      case "mainnet" -> "https://cardano-mainnet.blockfrost.io/api/v0/";
      case "preview" -> "https://cardano-preview.blockfrost.io/api/v0/";
      default -> "https://cardano-preprod.blockfrost.io/api/v0/";
    };
  }

  /** Map a network name to a Cardano network; anything but a known name defaults to testnet. */
  static Network networkFor(String name) {
    if (name == null) {
      return Networks.testnet();
    }
    return switch (name.trim().toLowerCase()) {
      case "mainnet" -> Networks.mainnet();
      case "preprod" -> Networks.preprod();
      case "preview" -> Networks.preview();
      default -> Networks.testnet();
    };
  }

  /** Split "--k v", "--k=v", and bare "--flag" from positional arguments. */
  private static Map<String, String> parseFlags(String[] args, int from, List<String> positionals) {
    Map<String, String> flags = new HashMap<>();
    for (int i = from; i < args.length; i++) {
      String a = args[i];
      if (a.startsWith("--")) {
        String body = a.substring(2);
        int eq = body.indexOf('=');
        if (eq >= 0) {
          flags.put(body.substring(0, eq), body.substring(eq + 1));
        } else if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
          flags.put(body, args[++i]);
        } else {
          flags.put(body, "true");
        }
      } else {
        positionals.add(a);
      }
    }
    return flags;
  }

  private static String pick(
      Map<String, String> flags,
      String flagKey,
      Map<String, String> env,
      String envKey,
      String def) {
    String fromFlag = flags.get(flagKey);
    if (!isBlank(fromFlag)) {
      return fromFlag;
    }
    String fromEnv = env.get(envKey);
    return isBlank(fromEnv) ? def : fromEnv;
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return isBlank(s) ? null : s;
  }

  private static String usage() {
    return String.join(
        "\n",
        "usage:",
        "  notary hash <file>             print the document's SHA-256 fingerprint",
        "  notary verify <file> <hashHex> check a file against a recorded fingerprint",
        "  notary proof <file> [name]     print the proof record to record on-chain",
        "  notary submit <file> [name]    record the proof on-chain -> tx hash",
        "",
        "submit options (flags win over env; keyless Koios by default):",
        "  --network preprod|preview|mainnet|testnet   (env POE_NETWORK, default preprod)",
        "  --backend koios|blockfrost|yaci              (env POE_BACKEND, default koios)",
        "  --backend-url <url>                          (env POE_BACKEND_URL, default per backend;"
            + " yaci -> http://localhost:8080/api/v1/)",
        "  --blockfrost-project-id <id>                 (env POE_BLOCKFROST_PROJECT_ID; blockfrost only)",
        "  --signing-key <file.sk> --address <addr>     (a cardano-cli key; env POE_SIGNING_KEY/POE_NOTARY_ADDRESS)",
        "  (or) POE_NOTARY_MNEMONIC=<mnemonic>          (a mnemonic; env only, it is a secret)");
  }
}
