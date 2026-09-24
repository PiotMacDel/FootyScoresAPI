package org.internship.footyscores;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.internship.footyscores.mapping.MatchMapper;
import org.internship.footyscores.model.MatchFixture;
import org.internship.footyscores.output.EndpointBuilder;
import org.internship.footyscores.stacy.RscCode;
import org.internship.footyscores.stacy.StacyClient;

/**
 * Produces the ordered, de-duplicated set of reference endpoints for the Paris 2024 football
 * tournament.
 */
public final class EndpointGenerator {

  private final StacyClient client;
  private final EndpointBuilder endpoints;

  public EndpointGenerator(StacyClient client, EndpointBuilder endpoints) {
    this.client = client;
    this.endpoints = endpoints;
  }

  public record GeneratedMatch(RscCode rsc, String endpoint, String file, MatchFixture fixture) {}

  public List<GeneratedMatch> generate(Consumer<String> progress) {
    List<JsonNode> units = matchUnits(client.schedule());
    progress.accept("Found " + units.size() + " football matches in the competition schedule");

    List<GeneratedMatch> generated = new ArrayList<>(units.size());
    for (JsonNode unit : units) {
      RscCode rsc = RscCode.parse(unit.path("code").asText());
      JsonNode result = client.matchResult(rsc.raw());
      MatchFixture fixture = MatchMapper.map(unit, result);
      generated.add(
          new GeneratedMatch(
              rsc,
              endpoints.endpoint(
                  rsc, fixture.kickoff(), fixture.teams().home(), fixture.teams().away()),
              endpoints.fileName(
                  rsc, fixture.kickoff(), fixture.teams().home(), fixture.teams().away()),
              fixture));
      progress.accept(
          "  "
              + fixture.kickoff()
              + "  "
              + fixture.teams().home()
              + " vs "
              + fixture.teams().away());
    }

    // Deterministic ordering: kickoff instant first, RSC code as a stable tie-breaker.
    generated.sort(
        Comparator.comparing(
                (GeneratedMatch m) -> Instant.from(OffsetDateTime.parse(m.fixture().kickoff())))
            .thenComparing(m -> m.rsc().raw()));

    assertUnique(generated);
    return generated;
  }

  /**
   * The football feed also contains victory ceremonies, which are units with a single participant
   * and a {@code VIC*} phase code. Only two-team competition units are matches.
   */
  private static List<JsonNode> matchUnits(JsonNode schedule) {
    List<JsonNode> units = new ArrayList<>();
    for (JsonNode unit : schedule.path("schedules")) {
      String code = unit.path("code").asText(null);
      if (code == null) {
        continue;
      }
      RscCode rsc = RscCode.parse(code);
      if (rsc.isFootball() && !rsc.isCeremony() && unit.path("start").size() == 2) {
        units.add(unit);
      }
    }
    return units;
  }

  private static void assertUnique(List<GeneratedMatch> generated) {
    Set<String> seen = new HashSet<>();
    for (GeneratedMatch match : generated) {
      if (!seen.add(match.endpoint())) {
        throw new IllegalStateException("Duplicate endpoint generated: " + match.endpoint());
      }
    }
  }
}
