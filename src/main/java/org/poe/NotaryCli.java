package org.poe;

import java.nio.file.Path;
import java.time.Instant;

/**
 * A tiny command-line front end for the notary, so it is a usable tool, not just a library.
 *
 * <pre>
 *   notary hash &lt;file&gt;              # print the document's SHA-256 fingerprint
 *   notary verify &lt;file&gt; &lt;hashHex&gt;  # check a file against a recorded fingerprint -> MATCH / NO MATCH
 *   notary proof &lt;file&gt; [name]      # print the proof record you would record on-chain
 * </pre>
 *
 * Recording a proof on-chain needs a backend + key, so it is done via {@code Notary.record(...)}
 * (the integration path / the testnet runbook), not from this offline CLI.
 */
public final class NotaryCli {

  private NotaryCli() {}

  public static void main(String[] args) {
    System.out.println(run(args));
  }

  /** Pure entry point (returns the output) so it can be unit-tested without touching stdout. */
  public static String run(String[] args) {
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
        String name = args.length > 2 ? args[2] : "";
        ProofRecord p =
            new ProofRecord(
                DocumentFingerprint.ofFile(Path.of(args[1])).hex(),
                DocumentFingerprint.ALGORITHM,
                Instant.now().toString(),
                name);
        yield "hash=" + p.hashHex() + " alg=" + p.algorithm() + " name=" + p.name();
      }
      default -> usage();
    };
  }

  private static String usage() {
    return String.join(
        "\n",
        "usage:",
        "  notary hash <file>             print the document's SHA-256 fingerprint",
        "  notary verify <file> <hashHex> check a file against a recorded fingerprint",
        "  notary proof <file> [name]     print the proof record to record on-chain");
  }
}
