package org.internship.footyscores.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import org.internship.footyscores.EndpointGenerator;
import org.internship.footyscores.model.EndpointIndex;
import org.internship.footyscores.output.EndpointBuilder;
import org.internship.footyscores.output.Json;
import org.internship.footyscores.stacy.StacyClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "footyscores-endpoints",
    mixinStandardHelpOptions = true,
    version = "footyscores-endpoints 1.0",
    sortOptions = false,
    description =
        "Generates the expected FootyScores API endpoint and reference payload for every "
            + "football match of the Paris 2024 Olympic Games.")
public final class GenerateCommand implements Callable<Integer> {

  private static final String ORDERING =
      "Ascending by kickoff instant (UTC), then by Olympic RSC code as a stable tie-breaker.";
  // Value used only for warning if the number of generated matches is different from the expected number.
  private static final int EXPECTED_MATCH_COUNT = 58;
  private static final String ENDPOINT_PREFIX = "api/v1/paris-2024/football";

  @Option(
      names = {"-o", "--output"},
      paramLabel = "DIR",
      description = "Directory for generated files. Default: ${DEFAULT-VALUE}")
  Path output = Path.of("out");

  @Option(
      names = {"--base-url"},
      paramLabel = "URL",
      description =
          "Prepended to every endpoint, e.g. https://api.footyscores.example. "
              + "Default: empty (relative paths).")
  String baseUrl = "";

  @Option(
      names = {"--endpoint-prefix"},
      paramLabel = "PATH",
      description = "Endpoint path prefix. Default: ${DEFAULT-VALUE}")
  String endpointPrefix = ENDPOINT_PREFIX;

  @Option(
      names = {"--source-url"},
      paramLabel = "URL",
      description = "Origin of the Olympic schedule data. Default: ${DEFAULT-VALUE}")
  String sourceUrl = StacyClient.DEFAULT_BASE_URL;

  @Option(
      names = {"--snapshot-dir"},
      paramLabel = "DIR",
      description =
          "Read-through cache of the raw upstream JSON. Existing files are reused, "
              + "missing ones are downloaded and stored. Guarantees reproducible runs.")
  Path snapshotDir;

  @Option(
      names = {"--offline"},
      description = "Fail instead of performing network calls. Requires --snapshot-dir.")
  boolean offline;

  @Option(
      names = {"--lang"},
      paramLabel = "CODE",
      description = "Upstream language code. Default: ${DEFAULT-VALUE}")
  String lang = "ENG";

  @Option(
      names = {"--endpoints-only"},
      description = "Print the endpoint list to stdout and skip writing reference payloads.")
  boolean endpointsOnly;

  @Option(
      names = {"--quiet", "-q"},
      description = "Suppress progress output.")
  boolean quiet;

  @Override
  public Integer call() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    ObjectWriter writer = Json.prettyWriter(mapper);

    StacyClient client = new StacyClient(sourceUrl, snapshotDir, offline, lang, mapper);
    EndpointGenerator generator =
        new EndpointGenerator(client, new EndpointBuilder(baseUrl, endpointPrefix));

    List<EndpointGenerator.GeneratedMatch> matches = generator.generate(this::progress);

    if (matches.size() != EXPECTED_MATCH_COUNT) {
      progress(
          "WARNING: expected " + EXPECTED_MATCH_COUNT + " matches but generated " + matches.size());
    }

    if (endpointsOnly) {
      PrintWriter out = new PrintWriter(System.out, true, StandardCharsets.UTF_8);
      matches.forEach(m -> out.println(m.endpoint()));
      out.flush();
      return 0;
    }

    writeAll(matches, writer);
    progress("Wrote " + matches.size() + " matches to " + output.toAbsolutePath());
    return 0;
  }

  private void writeAll(List<EndpointGenerator.GeneratedMatch> matches, ObjectWriter writer)
      throws IOException {
    Path matchesDir = output.resolve("matches");
    Files.createDirectories(matchesDir);

    List<EndpointIndex.Endpoint> index =
        matches.stream()
            .map(
                m ->
                    new EndpointIndex.Endpoint(
                        m.rsc().raw(), m.endpoint(), "matches/" + m.file()))
            .toList();

    EndpointIndex endpointIndex =
        new EndpointIndex(
            "Olympic Games Paris 2024",
            "Football",
            sourceUrl + "/en/paris-2024/competition-schedule",
            ORDERING,
            matches.size(),
            index);

    writeJson(output.resolve("endpoints.json"), endpointIndex, writer);
    for (EndpointGenerator.GeneratedMatch match : matches) {
      writeJson(matchesDir.resolve(match.file()), match.fixture(), writer);
    }
  }

  private static void writeJson(Path target, Object value, ObjectWriter writer) throws IOException {
    Json.write(target, value, writer);
  }

  private void progress(String message) {
    if (!quiet) {
      System.err.println(message);
    }
  }
}
