package org.internship.footyscores.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Reference representation of a single football match, mirroring the structure of {@code
 * example.json}.
 */
public record MatchFixture(
    Competition competition,
    Venue venue,
    String kickoff,
    String status,
    Teams teams,
    Score score,
    List<Scorer> scorers,
    Lineups lineups) {

  public MatchFixture {
    Require.notNull(competition, "competition");
    Require.notNull(venue, "venue");
    Require.notBlank(kickoff, "kickoff");
    Require.notBlank(status, "status");
    Require.notNull(teams, "teams");
    Require.notNull(score, "score");
    // If there are no scorers, the list should be empty, not null.
    Require.notNull(scorers, "scorers");
    Require.notNull(lineups, "lineups");
  }

  public record Competition(String name, String season, String round) {
    public Competition {
      Require.notBlank(name, "competition.name");
      Require.notBlank(season, "competition.season");
      Require.notBlank(round, "competition.round");
    }
  }

  public record Venue(String name, String city) {
    public Venue {
      Require.notBlank(name, "venue.name");
      Require.notBlank(city, "venue.city");
    }
  }

  public record Teams(String home, String away) {
    public Teams {
      Require.notBlank(home, "teams.home");
      Require.notBlank(away, "teams.away");
    }
  }

  public record Score(Integer home, Integer away, HalfTime halfTime) {
    public Score {
      Require.notNull(home, "score.home");
      Require.notNull(away, "score.away");
      Require.notNull(halfTime, "score.halfTime");
    }
  }

  public record HalfTime(Integer home, Integer away) {
    public HalfTime {
      Require.notNull(home, "score.halfTime.home");
      Require.notNull(away, "score.halfTime.away");
    }
  }

  // Assists are intentionally left unvalidated and NON_NULL: they are optional.
  public record Scorer(
      String team,
      String player,
      Integer minute,
      @JsonInclude(JsonInclude.Include.NON_NULL) String assist,
      String type) {
    public Scorer {
      Require.notBlank(team, "scorers[].team");
      Require.notBlank(player, "scorers[].player");
      Require.notNull(minute, "scorers[].minute");
      Require.notBlank(type, "scorers[].type");
    }
  }

  public record Lineups(TeamLineup home, TeamLineup away) {
    public Lineups {
      Require.notNull(home, "lineups.home");
      Require.notNull(away, "lineups.away");
    }
  }

  public record TeamLineup(
      String team, String formation, String coach, List<Player> startingXI, List<Player> bench) {
    public TeamLineup {
      Require.notBlank(team, "lineups[].team");
      Require.notBlank(formation, "lineups[].formation");
      Require.notBlank(coach, "lineups[].coach");
      Require.notNull(startingXI, "lineups[].startingXI");
      Require.notNull(bench, "lineups[].bench");
    }
  }

  public record Player(String name, Integer number, String position) {
    public Player {
      Require.notBlank(name, "lineups[].players[].name");
      Require.notNull(number, "lineups[].players[].number");
      Require.notBlank(position, "lineups[].players[].position");
    }
  }
}
