package org.internship.footyscores;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.internship.footyscores.model.MatchFixture;
import org.internship.footyscores.output.EndpointBuilder;
import org.internship.footyscores.stacy.RscCode;
import org.internship.footyscores.stacy.StacyClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * End-to-end verification of the acceptance criteria, executed against the committed snapshot of
 * the official Paris 2024 schedule. Runs fully offline.
 */
@EnabledIf("snapshotAvailable")
class EndpointGeneratorTest {

  private static final Path SNAPSHOT = Path.of("snapshot");
  private static final int EXPECTED_MATCHES = 58;
  private static final String EXPECTED_BASE_PREFIX = "/api/v1/paris-2024/football";

  private static List<EndpointGenerator.GeneratedMatch> matches;

  static boolean snapshotAvailable() {
    return Files.isDirectory(SNAPSHOT);
  }

  @BeforeAll
  static void generate() {
    StacyClient client =
        new StacyClient(StacyClient.DEFAULT_BASE_URL, SNAPSHOT, true, "ENG", new ObjectMapper());
    matches =
        new EndpointGenerator(client, new EndpointBuilder("", EXPECTED_BASE_PREFIX))
            .generate(message -> {});
  }

  @Test
  void coversEveryFootballMatchWithoutOmissions() {
    // Men: 24 group + 8 knockout = 32. Women: 18 group + 8 knockout = 26.
    assertThat(matches).hasSize(EXPECTED_MATCHES);

    Map<String, Long> byGender =
        matches.stream()
            .collect(Collectors.groupingBy(m -> m.rsc().genderSlug(), Collectors.counting()));
    assertThat(byGender).containsEntry("men", 32L).containsEntry("women", 26L);
  }

  @Test
  void excludesVictoryCeremonies() {
    assertThat(matches).noneMatch(m -> m.rsc().isCeremony());
  }

  @Test
  void endpointsAreUniquePerMatch() {
    assertThat(matches)
        .extracting(EndpointGenerator.GeneratedMatch::endpoint)
        .doesNotHaveDuplicates();
    assertThat(matches).extracting(EndpointGenerator.GeneratedMatch::file).doesNotHaveDuplicates();
    assertThat(matches).extracting(m -> m.rsc().raw()).doesNotHaveDuplicates();
  }

  @Test
  void endpointsFollowTheDocumentedStructure() {
    assertThat(matches)
        .allSatisfy(
            match ->
                assertThat(match.endpoint())
                    .matches(
                        EXPECTED_BASE_PREFIX
                            + "/(men|women)/matches/\\d{4}-\\d{2}-\\d{2}/[a-z0-9-]+-vs-[a-z0-9-]+"));
  }

  @Test
  void outputIsSortedByKickoffThenRscCode() {
    List<String> keys =
        matches.stream().map(m -> m.fixture().kickoff() + "|" + m.rsc().raw()).toList();
    assertThat(keys).isSorted();
  }

  @Test
  void generationIsDeterministic() {
    StacyClient client =
        new StacyClient(StacyClient.DEFAULT_BASE_URL, SNAPSHOT, true, "ENG", new ObjectMapper());
    List<EndpointGenerator.GeneratedMatch> second =
        new EndpointGenerator(client, new EndpointBuilder("", EXPECTED_BASE_PREFIX))
            .generate(message -> {});

    assertThat(second)
        .extracting(EndpointGenerator.GeneratedMatch::endpoint)
        .containsExactlyElementsOf(
            matches.stream().map(EndpointGenerator.GeneratedMatch::endpoint).toList());
    assertThat(second)
        .extracting(EndpointGenerator.GeneratedMatch::fixture)
        .containsExactlyElementsOf(
            matches.stream().map(EndpointGenerator.GeneratedMatch::fixture).toList());
  }

  @Test
  void everyFixtureIsFullyPopulated() {
    assertThat(matches)
        .allSatisfy(
            match -> {
              MatchFixture fixture = match.fixture();
              assertThat(fixture.competition().name()).isEqualTo("Olympic Games Paris 2024");
              assertThat(fixture.competition().round()).isNotBlank();
              assertThat(fixture.venue().name()).isNotBlank();
              assertThat(fixture.venue().city()).isNotBlank();
              assertThat(fixture.kickoff())
                  .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+\\d{2}:\\d{2}");
              assertThat(fixture.status()).isIn("FT", "AET", "PEN");
              assertThat(fixture.teams().home()).isNotBlank();
              assertThat(fixture.teams().away()).isNotEqualTo(fixture.teams().home());
              assertThat(fixture.score().home()).isNotNull();
              assertThat(fixture.score().away()).isNotNull();
              assertThat(fixture.lineups().home().startingXI()).hasSize(11);
              assertThat(fixture.lineups().away().startingXI()).hasSize(11);
              assertThat(fixture.lineups().home().formation()).isNotBlank();
              assertThat(fixture.lineups().home().coach()).isNotBlank();
              assertThat(fixture.lineups().away().coach()).isNotBlank();
            });
  }

  @Test
  void fallsBackToInterimCoachWhenNoHeadCoachIsRecorded() {
    // Canada's women's head coach was removed mid-tournament, leaving no COACH entry upstream.
    MatchFixture canada =
        matches.stream()
            .filter(m -> m.endpoint().endsWith("/2024-08-03/canada-vs-germany"))
            .map(EndpointGenerator.GeneratedMatch::fixture)
            .findFirst()
            .orElseThrow();
    assertThat(canada.lineups().home().coach()).isEqualTo("Andy Spence");
  }

  @Test
  void knownMatchIsMappedCorrectly() {
    MatchFixture finalMatch =
        matches.stream()
            .filter(
                m ->
                    m.endpoint()
                        .equals(EXPECTED_BASE_PREFIX + "/men/matches/2024-08-09/france-vs-spain"))
            .map(EndpointGenerator.GeneratedMatch::fixture)
            .findFirst()
            .orElseThrow();

    assertThat(finalMatch.competition().round()).isEqualTo("Gold medal match");
    assertThat(finalMatch.venue().name()).isEqualTo("Parc des Princes");
    assertThat(finalMatch.venue().city()).isEqualTo("Paris");
    assertThat(finalMatch.status()).isEqualTo("AET");
    assertThat(finalMatch.score().home()).isEqualTo(3);
    assertThat(finalMatch.score().away()).isEqualTo(5);
    assertThat(finalMatch.lineups().home().coach()).isEqualTo("Thierry Henry");
    assertThat(finalMatch.scorers())
        .first()
        .extracting(MatchFixture.Scorer::player, MatchFixture.Scorer::minute)
        .containsExactly("Enzo Millot", 11);
  }

  @Test
  void medalMatchesAreLabelled() {
    Map<String, List<String>> roundsByEndpoint =
        matches.stream()
            .collect(
                Collectors.groupingBy(
                    m -> m.fixture().competition().round(),
                    Collectors.mapping(
                        EndpointGenerator.GeneratedMatch::endpoint, Collectors.toList())));

    assertThat(roundsByEndpoint.get("Gold medal match")).hasSize(2);
    assertThat(roundsByEndpoint.get("Bronze medal match")).hasSize(2);
    assertThat(roundsByEndpoint.get("Semi-final")).hasSize(4);
    assertThat(roundsByEndpoint.get("Quarter-final")).hasSize(8);
  }

  @Test
  void groupStageRoundsAreNamed() {
    Function<String, Long> count =
        round ->
            matches.stream().filter(m -> round.equals(m.fixture().competition().round())).count();

    assertThat(count.apply("Group A")).isEqualTo(12);
    assertThat(count.apply("Group D")).isEqualTo(6); // men only
  }

  @Test
  void rscCodeParsing() {
    RscCode gold = RscCode.parse("FBLMTEAM11------------FNL-000100--");
    assertThat(gold.genderSlug()).isEqualTo("men");
    assertThat(gold.round()).isEqualTo("Gold medal match");

    RscCode bronze = RscCode.parse("FBLWTEAM11------------FNL-000200--");
    assertThat(bronze.genderSlug()).isEqualTo("women");
    assertThat(bronze.round()).isEqualTo("Bronze medal match");

    assertThat(RscCode.parse("FBLMTEAM11------------VICTMEDAL---").isCeremony()).isTrue();
    assertThat(RscCode.parse("FBLWTEAM11------------GPC-000100--").round()).isEqualTo("Group C");
  }

  @Test
  void slugsAreAsciiAndStable() {
    assertThat(EndpointBuilder.slug("United States of America"))
        .isEqualTo("united-states-of-america");
    assertThat(EndpointBuilder.slug("Côte d'Ivoire")).isEqualTo("cote-d-ivoire");
    assertThat(EndpointBuilder.localDate("2024-08-09T18:00:00+02:00")).isEqualTo("2024-08-09");
  }
}
