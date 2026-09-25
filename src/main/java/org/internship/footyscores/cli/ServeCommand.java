package org.internship.footyscores.cli;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import org.internship.footyscores.model.EndpointIndex;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
    name = "serve",
    description = "Starts a local HTTP server mocking the generated API endpoints.")
public final class ServeCommand implements Callable<Integer> {

  @Option(
      names = {"-d", "--dir"},
      description = "Directory containing endpoints.json",
      defaultValue = "out")
  Path dir;

  @Option(
      names = {"-p", "--port"},
      description = "HTTP port to listen on",
      defaultValue = "8080")
  int port;

  @Override
  public Integer call() throws Exception {
    Path indexPath = dir.resolve("endpoints.json");
    if (!Files.exists(indexPath)) {
      System.err.println("Error: " + indexPath + " not found. Run the generator first.");
      return 1;
    }

    ObjectMapper mapper = new ObjectMapper();
    EndpointIndex index = mapper.readValue(indexPath.toFile(), EndpointIndex.class);

    WireMockServer server = new WireMockServer(WireMockConfiguration.options().port(port));
    server.start();

    for (EndpointIndex.Endpoint endpoint : index.endpoints()) {
      Path jsonFile = dir.resolve(endpoint.file());
      if (Files.exists(jsonFile)) {
        String jsonBody = Files.readString(jsonFile);
        String urlPath =
            endpoint.endpoint().startsWith("/") ? endpoint.endpoint() : "/" + endpoint.endpoint();

        server.stubFor(
            get(urlEqualTo(urlPath))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(200)
                        .withBody(jsonBody)));
      }
    }

    System.out.println("Mock API is running on http://localhost:" + port);
    System.out.println("Serving " + index.matchCount() + " endpoints. Press Ctrl+C to stop.");

    Thread.currentThread().join();
    return 0;
  }
}
