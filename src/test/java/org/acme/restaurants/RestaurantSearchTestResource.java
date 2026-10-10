package org.acme.restaurants;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

public class RestaurantSearchTestResource implements QuarkusTestResourceLifecycleManager {
  private static final String INDEX = RestaurantSearchIndex.INDEX;
  private static final String MENU_INDEX = RestaurantSearchIndex.MENU_INDEX;
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private final GenericContainer<?> elasticsearch = createContainer();

  private static URI elasticsearchUri;

  @Override
  public Map<String, String> start() {
    elasticsearch.start();
    elasticsearchUri = URI.create(
        "http://" + elasticsearch.getHost() + ":" + elasticsearch.getMappedPort(9200));
    return Map.of("quarkus.rest-client.elasticsearch.url", elasticsearchUri.toString());
  }

  @Override
  public void stop() {
    elasticsearch.close();
  }

  private GenericContainer<?> createContainer() {
    GenericContainer<?> container = new GenericContainer<>(
        DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:8.17.4"));
    container.withEnv("discovery.type", "single-node");
    container.withEnv("xpack.security.enabled", "false");
    container.withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m");
    container.withExposedPorts(9200);
    container.waitingFor(
        Wait.forHttp("/").forStatusCode(200).withStartupTimeout(Duration.ofMinutes(3)));
    return container;
  }

  static void reset() {
    for (String index : new String[] {INDEX, MENU_INDEX}) {
      HttpResponse<String> response = send("POST", "/" + index + "/_delete_by_query?refresh=true",
          "{\"query\":{\"match_all\":{}}}");
      if (response.statusCode() != 200 && response.statusCode() != 404) {
        throw new IllegalStateException(
            "Could not clear Elasticsearch test index (HTTP " + response.statusCode() + "): "
                + response.body());
      }
    }
  }

  static void refresh() {
    for (String index : new String[] {INDEX, MENU_INDEX}) {
      HttpResponse<String> response = send("POST", "/" + index + "/_refresh", "");
      if (response.statusCode() != 200 && response.statusCode() != 404) {
        throw new IllegalStateException(
            "Could not refresh Elasticsearch test index (HTTP " + response.statusCode() + "): "
                + response.body());
      }
    }
  }

  static void indexRestaurants(int count) {
    StringBuilder bulk = new StringBuilder(count * 180);
    for (int id = 1; id <= count; id++) {
      bulk.append("{\"index\":{\"_id\":\"").append(id).append("\"}}\n")
          .append("{\"id\":").append(id)
          .append(",\"name\":\"restaurant-total-threshold\",")
          .append("\"location\":{\"lat\":0,\"lon\":0}}\n");
    }
    HttpResponse<String> response = send(
        "POST", "/" + INDEX + "/_bulk?refresh=true", bulk.toString());
    if (response.statusCode() != 200 || response.body().contains("\"errors\":true")) {
      throw new IllegalStateException(
          "Could not index Elasticsearch test documents (HTTP " + response.statusCode() + "): "
              + response.body());
    }
  }

  private static HttpResponse<String> send(String method, String path, String body) {
    if (elasticsearchUri == null) {
      throw new IllegalStateException("Elasticsearch test container has not started");
    }
    HttpRequest request = HttpRequest.newBuilder(elasticsearchUri.resolve(path))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", path.contains("/_bulk")
            ? "application/x-ndjson"
            : "application/json")
        .method(method, body.isEmpty()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body))
        .build();
    try {
      return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new IllegalStateException("Could not reach Elasticsearch test container", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while accessing Elasticsearch test container", exception);
    }
  }
}
