package org.internship.footyscores.output;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.internship.footyscores.stacy.RscCode;

/**
 * Builds the reference endpoint path for a match.
 *
 * <p>Structure: {@code api/v1/paris-2024/football/{gender}/matches/{yyyy-MM-dd}/{home}-vs-{away}}
 *
 * <p>The tuple (gender, local match date, home team, away team) is unique across the tournament, so
 * each endpoint addresses exactly one match.
 */
public final class EndpointBuilder {

  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  private final String baseUrl;
  private final String prefix;

  public EndpointBuilder(String baseUrl, String prefix) {
    this.baseUrl = baseUrl == null ? "" : stripTrailingSlash(baseUrl);
    this.prefix = stripTrailingSlash(prefix.startsWith("/") ? prefix : "/" + prefix);
  }

  public String endpoint(RscCode rsc, String kickoff, String homeTeam, String awayTeam) {
    return baseUrl
        + "%s/%s/matches/%s/%s-vs-%s"
            .formatted(
                prefix, rsc.genderSlug(), localDate(kickoff), slug(homeTeam), slug(awayTeam));
  }

  public String fileName(RscCode rsc, String kickoff, String homeTeam, String awayTeam) {
    return "%s/%s-%s-vs-%s.json"
        .formatted(rsc.genderSlug(), localDate(kickoff), slug(homeTeam), slug(awayTeam));
  }

  public static String localDate(String kickoff) {
    return OffsetDateTime.parse(kickoff).format(DATE);
  }

  public static String slug(String value) {
    String ascii = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    String slug =
        ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    return slug.isEmpty() ? "unknown" : slug;
  }

  private static String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
