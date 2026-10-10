package org.acme.restaurants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RestaurantSearchIndex {
  private static final String INDEX = "restaurants";
  private static final String MAPPING = """
      {
        "settings": {
          "analysis": {
            "normalizer": {
              "lowercase_normalizer": {
                "type": "custom",
                "filter": ["lowercase"]
              }
            }
          }
        },
        "mappings": {
          "properties": {
            "id": {"type": "long"},
            "name": {"type": "text"},
            "description": {"type": "text"},
            "address": {"type": "text"},
            "city": {
              "type": "text",
              "fields": {"keyword": {"type": "keyword", "normalizer": "lowercase_normalizer"}}
            },
            "cuisine": {
              "type": "text",
              "fields": {"keyword": {"type": "keyword", "normalizer": "lowercase_normalizer"}}
            },
            "latitude": {"type": "double"},
            "longitude": {"type": "double"},
            "averageRating": {"type": "double"},
            "reviewCount": {"type": "long"},
            "location": {"type": "geo_point"}
          }
        }
      }
      """;

  private final ElasticsearchClient client;
  private final ObjectMapper objectMapper;

  RestaurantSearchIndex(@RestClient ElasticsearchClient client, ObjectMapper objectMapper) {
    this.client = client;
    this.objectMapper = objectMapper;
  }

  public void ensureIndex() {
    try (Response response = client.indexExists(INDEX)) {
      if (response.getStatus() == 200) {
        return;
      }
      if (response.getStatus() != 404) {
        throw failed("check index", response);
      }
    }
    try (Response response = client.createIndex(INDEX, MAPPING)) {
      if (response.getStatus() != 200 && response.getStatus() != 201) {
        throw failed("create index", response);
      }
    }
  }

  public void bulk(String operations, int expectedItems) {
    try (Response response = client.bulk(INDEX, operations)) {
      if (response.getStatus() != 200) {
        throw failed("bulk index restaurants", response);
      }
      String body = response.readEntity(String.class);
      JsonNode items;
      try {
        JsonNode root = objectMapper.readTree(body);
        if (root == null || !root.path("errors").isBoolean()) {
          throw new IllegalStateException("Elasticsearch returned an invalid bulk response: " + body);
        }
        items = root.path("items");
      } catch (java.io.IOException exception) {
        throw new IllegalStateException("Elasticsearch returned an invalid bulk response", exception);
      }
      if (!items.isArray() || items.size() != expectedItems) {
        throw new IllegalStateException("Elasticsearch returned an incomplete bulk response: " + body);
      }
      for (JsonNode item : items) {
        if (!item.isObject() || item.size() != 1) {
          throw new IllegalStateException("Elasticsearch returned an invalid bulk item: " + item);
        }
        var result = item.fields().next();
        int status = result.getValue().path("status").asInt(-1);
        if ((status < 200 || status >= 300)
            && !(result.getKey().equals("delete") && status == 404)) {
          throw new IllegalStateException(
              "Elasticsearch bulk operation failed (HTTP " + status + "): "
                  + result.getValue().path("error"));
        }
      }
    }
  }

  public Response search(String query) {
    return client.search(INDEX, query);
  }

  private IllegalStateException failed(String operation, Response response) {
    String details = response.hasEntity() ? response.readEntity(String.class) : "";
    return new IllegalStateException(
        "Elasticsearch could not " + operation + " (HTTP " + response.getStatus() + "): " + details);
  }
}
