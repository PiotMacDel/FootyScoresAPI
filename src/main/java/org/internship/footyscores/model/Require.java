package org.internship.footyscores.model;

/**
 * Fail-fast validation for the reference-payload records.
 */
final class Require {

  private Require() {}

  static void notBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be null or blank");
    }
  }

  static <T> void notNull(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " must not be null");
    }
  }
}
