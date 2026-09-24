package org.internship.footyscores.model;

import java.util.List;

/**
 * Representation of available endpoints for football match data.
 */
public record EndpointIndex(
    String competition,
    String discipline,
    String source,
    String ordering,
    int matchCount,
    List<Endpoint> endpoints) {

  public EndpointIndex {
    Require.notBlank(competition, "competition");
    Require.notBlank(discipline, "discipline");
    Require.notBlank(source, "source");
    Require.notBlank(ordering, "ordering");
    if (matchCount < 0) {
      throw new IllegalArgumentException("matchCount must not be negative");
    }
    Require.notNull(endpoints, "endpoints");
  }

  public record Endpoint(String matchId, String endpoint, String file) {
    public Endpoint {
      Require.notBlank(matchId, "endpoints[].matchId");
      Require.notBlank(endpoint, "endpoints[].endpoint");
      Require.notBlank(file, "endpoints[].file");
    }
  }
}
