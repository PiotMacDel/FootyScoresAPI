package org.internship.footyscores.api;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.internship.footyscores.Main;
import org.internship.footyscores.model.EndpointIndex;
import org.internship.footyscores.model.MatchFixture;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class ApiIntegrationTest {

  @TempDir static Path tempOutDir;

  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newHttpClient();

  @RegisterExtension
  static WireMockExtension wiremock =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @BeforeAll
  static void generateTestData() {

    boolean hasSnapshot = java.nio.file.Files.exists(Path.of("snapshot"));

    java.util.List<String> args =
        new java.util.ArrayList<>(
            java.util.List.of(
                "generate", "-o", tempOutDir.toString(), "--snapshot-dir", "snapshot", "--quiet"));

    if (hasSnapshot) {
      args.add("--offline");
    }

    int exitCode =
        new CommandLine(new Main())
            .setCaseInsensitiveEnumValuesAllowed(true)
            .execute(args.toArray(new String[0]));

    assertThat(exitCode)
        .as("Data generation CLI command should succeed (Offline mode: %b)", hasSnapshot)
        .isEqualTo(0);
  }

  @BeforeEach
  void setupMockServer() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    File indexFile = tempOutDir.resolve("endpoints.json").toFile();
    EndpointIndex index = mapper.readValue(indexFile, EndpointIndex.class);

    for (EndpointIndex.Endpoint endpoint : index.endpoints()) {
      File jsonFile = tempOutDir.resolve(endpoint.file()).toFile();
      String jsonBody = java.nio.file.Files.readString(jsonFile.toPath());

      String urlPath =
          endpoint.endpoint().startsWith("/") ? endpoint.endpoint() : "/" + endpoint.endpoint();

      wiremock.stubFor(
          get(urlEqualTo(urlPath))
              .willReturn(
                  aResponse()
                      .withHeader("Content-Type", "application/json")
                      .withStatus(200)
                      .withBody(jsonBody)));
    }
  }

  @Test
  void everyEndpointShouldReturnValidMatchFixture() throws Exception {
    // Given
    File indexFile = tempOutDir.resolve("endpoints.json").toFile();
    EndpointIndex index = mapper.readValue(indexFile, EndpointIndex.class);

    assertThat(index.matchCount()).isEqualTo(58);

    for (EndpointIndex.Endpoint endpoint : index.endpoints()) {

      String urlPath =
          endpoint.endpoint().startsWith("/") ? endpoint.endpoint() : "/" + endpoint.endpoint();

      HttpRequest request =
          HttpRequest.newBuilder().uri(URI.create(wiremock.baseUrl() + urlPath)).GET().build();

      // When
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // Then
      assertThat(response.statusCode())
          .as("Endpoint %s should return 200 OK", urlPath)
          .isEqualTo(200);

      // Then.
      assertDoesNotThrow(
          () -> {
            MatchFixture fixture = mapper.readValue(response.body(), MatchFixture.class);

            assertThat(fixture.teams().home()).isNotNull();
            assertThat(fixture.teams().away()).isNotNull();
          },
          "Failed to parse valid MatchFixture for endpoint: " + urlPath);
    }
  }
}
