package org.internship.footyscores.stacy;

import java.util.Locale;

/**
 * Parser for Olympic Results System Codification codes, e.g. {@code
 * FBLMTEAM11------------FNL-000100--}.
 *
 * <pre>
 *   [0,3)   discipline      "FBL" (football)
 *   [3,4)   gender          "M" | "W"
 *   [22,26) phase           "GPA-" "GPB-" "GPC-" "GPD-" "QFNL" "SFNL" "FNL-" "VICT"
 *   [26,32) unit number     e.g. "000100"
 * </pre>
 */
public record RscCode(String raw, String discipline, String gender, String phase, String unit) {

  private static final int MIN_LENGTH = 32;

  public static RscCode parse(String raw) {
    if (raw == null || raw.length() < MIN_LENGTH) {
      throw new IllegalArgumentException("Not a valid RSC code: " + raw);
    }
    return new RscCode(
        raw,
        raw.substring(0, 3),
        raw.substring(3, 4),
        raw.substring(22, 26),
        raw.substring(26, 32));
  }

  public boolean isFootball() {
    return "FBL".equals(discipline);
  }

  /** Victory ceremonies share the football schedule feed but are not matches. */
  public boolean isCeremony() {
    return phase.startsWith("VIC");
  }

  public String genderSlug() {
    return switch (gender) {
      case "M" -> "men";
      case "W" -> "women";
      default -> "mixed";
    };
  }

  /** Human readable round name used for {@code competition.round}. */
  public String round() {
    String normalised = phase.replace("-", "");
    return switch (normalised) {
      case "QFNL" -> "Quarter-final";
      case "SFNL" -> "Semi-final";
      case "FNL" -> "000200".equals(unit) ? "Bronze medal match" : "Gold medal match";
      default -> normalised.startsWith("GP") ? "Group " + normalised.substring(2) : normalised;
    };
  }
}
