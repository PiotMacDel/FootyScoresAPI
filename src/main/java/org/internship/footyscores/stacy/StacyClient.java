package org.internship.footyscores.stacy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Reads the static JSON feeds that back the official Paris 2024 competition schedule at {@code
 * https://stacy.olympics.com/en/paris-2024/competition-schedule}.
 *
 * <p>The site is a React single page application; this client talks to the same {@code
 * /OG2024/data} documents the page itself fetches.
 *
 * <p>Responses can be mirrored into a snapshot directory. When a snapshot is present it is used as
 * the source of truth, which makes runs reproducible and allows offline/CI execution.
 */
public final class StacyClient {

  public static final String DEFAULT_BASE_URL = "https://stacy.olympics.com";
  private static final String DATA_ROOT = "/OG2024/data";
  private static final String COMPETITION = "OG2024";
  private static final String DISCIPLINE = "FBL";

  // The CDN rejects requests without a browser-like User-Agent with HTTP 403.
  private static final String USER_AGENT =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
          + "Chrome/126.0.0.0 Safari/537.36";

  private final HttpClient httpClient;
  private final ObjectMapper mapper;
  private final String baseUrl;
  private final Path snapshotDir;
  private final boolean offline;
  private final String language;

  public StacyClient(
      String baseUrl, Path snapshotDir, boolean offline, String language, ObjectMapper mapper) {
    this.baseUrl = stripTrailingSlash(baseUrl);
    this.snapshotDir = snapshotDir;
    this.offline = offline;
    this.language = language;
    this.mapper = mapper;
    this.httpClient =
        HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
  }

  /** Full football schedule (all units, including victory ceremonies). */
  public JsonNode schedule() {
    String name =
        "SCH_StartList~comp=%s~disc=%s~lang=%s.json".formatted(COMPETITION, DISCIPLINE, language);
    return fetch(name);
  }

  /** Head-to-head result document for a single match, containing lineups and play-by-play. */
  public JsonNode matchResult(String rsc) {
    String name =
        "RES_ByRSC_H2H~comp=%s~disc=%s~rscResult=%s~lang=%s.json"
            .formatted(COMPETITION, DISCIPLINE, rsc, language);
    return fetch(name);
  }

  private JsonNode fetch(String documentName) {
    Path cacheFile = snapshotDir == null ? null : snapshotDir.resolve(toFileName(documentName));
    if (cacheFile != null && Files.exists(cacheFile)) {
      return readJson(cacheFile);
    }
    if (offline) {
      throw new IllegalStateException(
          "Offline mode is enabled but no snapshot exists for "
              + documentName
              + (cacheFile == null ? " (no --snapshot-dir given)" : " at " + cacheFile));
    }
    String body = download(baseUrl + DATA_ROOT + "/" + encodePathSegment(documentName));
    if (cacheFile != null) {
      writeSnapshot(cacheFile, body);
    }
    return parse(body, documentName);
  }

  private String download(String url) {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();
    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() != 200) {
        throw new IllegalStateException("GET " + url + " returned HTTP " + response.statusCode());
      }
      return response.body();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to GET " + url, e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while fetching " + url, e);
    }
  }

  private JsonNode readJson(Path file) {
    try {
      return mapper.readTree(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read snapshot " + file, e);
    }
  }

  private JsonNode parse(String body, String documentName) {
    try {
      return mapper.readTree(body);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to parse JSON document " + documentName, e);
    }
  }

  private void writeSnapshot(Path file, String body) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(file, body, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to write snapshot " + file, e);
    }
  }

  /**
   * Only the RSC code inside the document name needs escaping; the {@code ~} separators and {@code
   * =} signs are literal parts of the path used by the upstream CDN.
   */
  private static String encodePathSegment(String documentName) {
    StringBuilder out = new StringBuilder(documentName.length());
    for (int i = 0; i < documentName.length(); i++) {
      char c = documentName.charAt(i);
      if (Character.isLetterOrDigit(c) || "~=._-".indexOf(c) >= 0) {
        out.append(c);
      } else {
        out.append(URLEncoder.encode(String.valueOf(c), StandardCharsets.UTF_8));
      }
    }
    return out.toString();
  }

  private static String toFileName(String documentName) {
    return documentName.replaceAll("[^A-Za-z0-9.=_-]", "_");
  }

  private static String stripTrailingSlash(String url) {
    return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
  }
}
