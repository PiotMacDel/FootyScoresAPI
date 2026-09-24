package org.internship.footyscores.mapping;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.internship.footyscores.model.MatchFixture;
import org.internship.footyscores.stacy.RscCode;

/**
 * Translates the upstream Olympic schedule/result documents into the {@code example.json} shape.
 */
public final class MatchMapper {

  private static final String COMPETITION_NAME = "Olympic Games Paris 2024";
  private static final String SEASON = "2024";

  /**
   * Periods that count towards the match scoreline, in chronological order. H1 - First half, H2 -
   * Second half, ET-H1 - First half of extra time, ET-H2 - Second half of extra time
   */
  private static final Map<String, Integer> MATCH_PERIODS =
      Map.of(
          "H1", 1,
          "H2", 2,
          "ET-H1", 3,
          "ET-H2", 4);

  private static final String PENALTY_SHOOTOUT_PERIOD = "PSO";

  private MatchMapper() {}

  /**
   * @param unit The upstream schedule document, containing the match metadata and team/squad
   *     information.
   * @param result The upstream result document, containing the official scoreline and play-by-play
   *     events.
   * @return A {@link MatchFixture} instance representing the mapped match data.
   */
  public static MatchFixture map(JsonNode unit, JsonNode result) {
    RscCode rsc = RscCode.parse(unit.path("code").asText());

    Map<String, TeamRef> teamsByCode = teamsByCode(unit);
    Sides sides = resolveSides(result, teamsByCode);
    Map<String, String> athleteNames = athleteNames(result);
    List<MatchFixture.Scorer> scorers = scorers(result, teamsByCode, athleteNames, sides);

    return new MatchFixture(
        new MatchFixture.Competition(COMPETITION_NAME, SEASON, rsc.round()),
        venue(unit),
        text(unit, "startDate"),
        status(unit, result),
        new MatchFixture.Teams(sides.home().name(), sides.away().name()),
        score(result, sides),
        scorers,
        lineups(result, sides));
  }

  /** Play-by-play events reference athletes by code only, so names are resolved from the squads. */
  private static Map<String, String> athleteNames(JsonNode result) {
    Map<String, String> byCode = new LinkedHashMap<>();
    for (JsonNode item : result.path("results").path("items")) {
      for (JsonNode entry : item.path("teamAthletes")) {
        JsonNode athlete = entry.path("athlete");
        String code = athlete.path("code").asText(null);
        if (code != null) {
          byCode.put(code, personName(athlete));
        }
      }
    }
    return byCode;
  }

  /** Home/away assignment. */
  private static Sides resolveSides(JsonNode result, Map<String, TeamRef> teamsByCode) {
    String homeCode = null;
    String awayCode = null;
    for (JsonNode item : result.path("results").path("items")) {
      String marker = eventUnitEntry(item, "HOME_AWAY");
      String teamCode = item.path("teamCode").asText(null);
      if ("HOME".equals(marker)) {
        homeCode = teamCode;
      } else if ("AWAY".equals(marker)) {
        awayCode = teamCode;
      }
    }
    return new Sides(teamsByCode.get(homeCode), teamsByCode.get(awayCode));
  }

  private static Map<String, TeamRef> teamsByCode(JsonNode unit) {
    Map<String, TeamRef> byCode = new LinkedHashMap<>();
    for (JsonNode start : sortedBy(unit.path("start"), n -> n.path("startOrder").asInt())) {
      JsonNode participant = start.path("participant");
      String code = start.path("teamCode").asText();
      byCode.put(
          code,
          new TeamRef(
              code,
              participant.path("name").asText(),
              participant
                  .path("organisation")
                  .path("code")
                  .asText()
                  .toLowerCase(java.util.Locale.ROOT)));
    }
    return byCode;
  }

  private static MatchFixture.Venue venue(JsonNode unit) {
    String name = text(unit, "venue", "description");
    String location = text(unit, "location", "description");
    String city = null;
    if (location != null) {
      int comma = location.lastIndexOf(',');
      city = comma >= 0 ? location.substring(comma + 1).trim() : location.trim();
    }
    return new MatchFixture.Venue(name, city);
  }

  /**
   * Football status conventions: {@code PEN} when decided by a shootout, {@code AET} after extra
   * time, {@code FT} for a completed regulation match.
   */
  private static String status(JsonNode unit, JsonNode result) {
    boolean shootout = false;
    boolean extraTime = false;
    for (JsonNode period : result.path("results").path("playByPlay")) {
      String subcode = period.path("subcode").asText();
      if (PENALTY_SHOOTOUT_PERIOD.equals(subcode) && !period.path("actions").isEmpty()) {
        shootout = true;
      } else if (subcode.startsWith("ET-H") && !period.path("actions").isEmpty()) {
        extraTime = true;
      }
    }
    if (shootout) {
      return "PEN";
    }
    if (extraTime) {
      return "AET";
    }
    boolean official = "OFFICIAL".equals(text(result, "results", "status", "code"));
    String scheduleStatus = Optional.ofNullable(text(unit, "status", "code")).orElse("");
    if (official || "FINISHED".equals(scheduleStatus)) {
      return "FT";
    }
    return switch (scheduleStatus) {
      case "SCHEDULED", "RESCHEDULED" -> "NS";
      case "POSTPONED" -> "PST";
      case "CANCELLED" -> "CANC";
      case "" -> null;
      default -> scheduleStatus;
    };
  }

  private static MatchFixture.Score score(JsonNode result, Sides sides) {
    Integer home = null;
    Integer away = null;
    for (JsonNode item : result.path("results").path("items")) {
      String teamCode = item.path("teamCode").asText(null);
      Integer value = asInteger(item.path("resultData").asText(null));
      if (sides.matchesHome(teamCode)) {
        home = value;
      } else if (sides.matchesAway(teamCode)) {
        away = value;
      }
    }
    if (home == null && away == null) {
      return null;
    }
    return new MatchFixture.Score(home, away, halfTime(result));
  }

  private static MatchFixture.HalfTime halfTime(JsonNode result) {
    for (JsonNode period : result.path("results").path("periods")) {
      if (!"H1".equals(period.path("p_code").asText())) {
        continue;
      }
      Integer home = asInteger(period.path("home").path("score").asText(null));
      Integer away = asInteger(period.path("away").path("score").asText(null));
      if (home != null && away != null) {
        return new MatchFixture.HalfTime(home, away);
      }
    }
    return null;
  }

  private static List<MatchFixture.Scorer> scorers(
      JsonNode result,
      Map<String, TeamRef> teamsByCode,
      Map<String, String> athleteNames,
      Sides sides) {
    record Timed(int period, int order, MatchFixture.Scorer scorer) {}
    List<Timed> timed = new ArrayList<>();

    for (JsonNode period : result.path("results").path("playByPlay")) {
      Integer periodOrder = MATCH_PERIODS.get(period.path("subcode").asText());
      if (periodOrder == null) {
        continue; // skip interval markers and the penalty shootout
      }
      for (JsonNode action : period.path("actions")) {
        if (!"GOAL".equals(action.path("pbpa_Result").asText())) {
          continue;
        }
        String type = action.path("pbpa_Action").asText();
        JsonNode competitor = action.path("competitors").path(0);
        TeamRef team = creditedTeam(competitor, type, teamsByCode, sides);
        timed.add(
            new Timed(
                periodOrder,
                action.path("pbpa_order").asInt(),
                new MatchFixture.Scorer(
                    team == null ? null : team.name(),
                    scorerName(competitor, athleteNames),
                    parseMinute(action.path("pbpa_When").asText(null)),
                    athleteByRole(competitor, "ASSIST", athleteNames),
                    goalType(type))));
      }
    }

    timed.sort(Comparator.comparingInt(Timed::period).thenComparingInt(Timed::order));
    return timed.stream().map(Timed::scorer).toList();
  }

  /**
   * {@code team} names the side the goal counts for. For own goals the upstream feed lists the team
   * of the player who scored into their own net, so the credit is flipped to the opponent.
   */
  private static TeamRef creditedTeam(
      JsonNode competitor, String type, Map<String, TeamRef> teamsByCode, Sides sides) {
    String code = competitor.path("pbpc_code").asText(null);
    if (!"OG".equals(type)) {
      return teamsByCode.get(code);
    }
    if (sides.matchesHome(code)) {
      return sides.away();
    }
    if (sides.matchesAway(code)) {
      return sides.home();
    }
    return null;
  }

  private static String goalType(String action) {
    return switch (action) {
      case "PEN" -> "penalty";
      case "FRD" -> "free_kick";
      case "OG" -> "own_goal";
      case "SHOT" -> "open_play";
      default -> action.toLowerCase(java.util.Locale.ROOT);
    };
  }

  /** Minutes arrive as {@code "22'"} or {@code "45' +4"}; the base minute is used. */
  static Integer parseMinute(String when) {
    if (when == null) {
      return null;
    }
    StringBuilder digits = new StringBuilder();
    for (int i = 0; i < when.length() && Character.isDigit(when.charAt(i)); i++) {
      digits.append(when.charAt(i));
    }
    return digits.isEmpty() ? null : Integer.valueOf(digits.toString());
  }

  /** Own-goal events carry no {@code SCR} role, so the sole listed athlete is used instead. */
  private static String scorerName(JsonNode competitor, Map<String, String> athleteNames) {
    String byRole = athleteByRole(competitor, "SCR", athleteNames);
    if (byRole != null) {
      return byRole;
    }
    JsonNode athletes = competitor.path("athletes");
    return athletes.size() == 1
        ? athleteNames.get(athletes.path(0).path("pbpat_code").asText())
        : null;
  }

  private static String athleteByRole(
      JsonNode competitor, String role, Map<String, String> athleteNames) {
    for (JsonNode athlete : competitor.path("athletes")) {
      if (role.equals(athlete.path("pbpat_role").asText())) {
        return athleteNames.get(athlete.path("pbpat_code").asText());
      }
    }
    return null;
  }

  private static MatchFixture.Lineups lineups(JsonNode result, Sides sides) {
    MatchFixture.TeamLineup home = null;
    MatchFixture.TeamLineup away = null;
    for (JsonNode item : result.path("results").path("items")) {
      String teamCode = item.path("teamCode").asText(null);
      if (sides.matchesHome(teamCode)) {
        home = teamLineup(item, sides.home());
      } else if (sides.matchesAway(teamCode)) {
        away = teamLineup(item, sides.away());
      }
    }
    return home == null && away == null ? null : new MatchFixture.Lineups(home, away);
  }

  private static MatchFixture.TeamLineup teamLineup(JsonNode item, TeamRef team) {
    List<MatchFixture.Player> startingXI = new ArrayList<>();
    List<MatchFixture.Player> bench = new ArrayList<>();

    for (JsonNode entry : sortedBy(item.path("teamAthletes"), n -> n.path("order").asInt())) {
      MatchFixture.Player player =
          new MatchFixture.Player(
              personName(entry.path("athlete")),
              asInteger(entry.path("bib").asText(null)),
              eventUnitEntry(entry, "POSITION"));
      if ("Y".equals(eventUnitEntry(entry, "STARTER"))) {
        startingXI.add(player);
      } else {
        bench.add(player);
      }
    }

    return new MatchFixture.TeamLineup(
        team == null ? null : team.name(),
        eventUnitEntry(item, "FORMATION"),
        headCoach(item),
        startingXI,
        bench);
  }

  /** Head coach lookup. */
  private static final List<String> COACH_FUNCTIONS = List.of("COACH", "SI_COA", "AST_COA");

  private static String headCoach(JsonNode item) {
    for (String function : COACH_FUNCTIONS) {
      for (JsonNode coach : item.path("teamCoaches")) {
        if (function.equals(coach.path("function").path("functionCode").asText())) {
          return personName(coach.path("coach"));
        }
      }
    }
    return null;
  }

  private static String personName(JsonNode person) {
    String given = person.path("givenName").asText(null);
    String family = person.path("familyName").asText(null);
    if (given != null && !given.isBlank() && family != null && !family.isBlank()) {
      return given.trim() + " " + family.trim();
    }
    String tvName = person.path("TVName").asText(null);
    String fallback =
        tvName != null && !tvName.isBlank() ? tvName : person.path("name").asText(null);
    return fallback == null ? null : titleCase(fallback);
  }

  /**
   * Capitalizes each whitespace- or hyphen-separated word, e.g. {@code "ANA VITORIA"} -> {@code
   * "Ana Vitoria"}.
   */
  static String titleCase(String value) {
    StringBuilder out = new StringBuilder(value.length());
    boolean startOfWord = true;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isLetter(c)) {
        out.append(startOfWord ? Character.toUpperCase(c) : Character.toLowerCase(c));
        startOfWord = false;
      } else {
        out.append(c);
        startOfWord = true;
      }
    }
    return out.toString();
  }

  private static String eventUnitEntry(JsonNode owner, String code) {
    for (JsonNode entry : owner.path("eventUnitEntries")) {
      if (code.equals(entry.path("eue_code").asText())) {
        String value = entry.path("eue_value").asText(null);
        return value == null || value.isBlank() ? null : value;
      }
    }
    return null;
  }

  private static List<JsonNode> sortedBy(
      JsonNode array, java.util.function.ToIntFunction<JsonNode> key) {
    List<JsonNode> nodes = new ArrayList<>();
    array.forEach(nodes::add);
    nodes.sort(Comparator.comparingInt(key));
    return nodes;
  }

  private static Integer asInteger(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return Integer.valueOf(value.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static String text(JsonNode node, String... path) {
    JsonNode current = node;
    for (String segment : path) {
      current = current.path(segment);
    }
    String value = current.asText(null);
    return value == null || value.isBlank() ? null : value;
  }

  private record TeamRef(String code, String name, String noc) {}

  private record Sides(TeamRef home, TeamRef away) {
    boolean matchesHome(String teamCode) {
      return home != null && home.code().equals(teamCode);
    }

    boolean matchesAway(String teamCode) {
      return away != null && away.code().equals(teamCode);
    }
  }
}
