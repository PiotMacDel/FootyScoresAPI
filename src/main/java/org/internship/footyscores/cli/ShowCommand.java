package org.internship.footyscores.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import org.internship.footyscores.model.EndpointIndex;
import org.internship.footyscores.model.MatchFixture;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
    name = "show",
    description = "Searches and prints matching reference payloads from the generated data.")
public final class ShowCommand implements Callable<Integer> {
  @Parameters(
      paramLabel = "FILTER",
      arity = "0..1",
      description =
          "Filter by endpoint, RSC code, or team names (e.g., spain or 2024-08-09). Omit to show all.")
  String filter = "";

  @Option(
      names = {"-d", "--dir"},
      description = "Directory containing generated files",
      defaultValue = "out")
  Path dir;

  @Override
  public Integer call() throws Exception {
    Path indexPath = dir.resolve("endpoints.json");
    if (!Files.exists(indexPath)) {
      System.err.println("Error: " + indexPath + " not found. Run the 'generate' command first.");
      return 1;
    }

    ObjectMapper mapper = new ObjectMapper();
    ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
    EndpointIndex index = mapper.readValue(indexPath.toFile(), EndpointIndex.class);

    String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
    int matchCount = 0;

    for (EndpointIndex.Endpoint endpoint : index.endpoints()) {
      Path jsonFile = dir.resolve(endpoint.file());
      if (!Files.exists(jsonFile)) continue;

      MatchFixture fixture = mapper.readValue(jsonFile.toFile(), MatchFixture.class);

      if (matchesFilter(endpoint, fixture, needle)) {
        if (matchCount > 0) {
          System.out.println();
        }
        System.out.println(writer.writeValueAsString(fixture));
        matchCount++;
      }
    }

    if (matchCount == 0) {
      System.err.println("No match found for filter: \"" + filter + "\"");
      return 1;
    }

    System.err.println("Printed " + matchCount + " match(es) matching \"" + filter + "\"");
    return 0;
  }

  private static boolean matchesFilter(
      EndpointIndex.Endpoint endpoint, MatchFixture fixture, String needle) {
    if (needle.isEmpty()) return true;
    return contains(endpoint.endpoint(), needle)
        || contains(endpoint.matchId(), needle)
        || contains(fixture.teams().home(), needle)
        || contains(fixture.teams().away(), needle);
  }

  private static boolean contains(String haystack, String needle) {
    return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
  }
}
