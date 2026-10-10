package org.acme.restaurants;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.inject.Inject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RestaurantSearchIndex {
  static final String INDEX = "restaurants";
  static final String MENU_INDEX = "restaurant-menu-items";
  private static final String RESTAURANT_MAPPING = """
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
            "openingHoursProvided": {"type": "boolean"},
            "openingPeriods": {
              "type": "nested",
              "properties": {
                "dayOfWeek": {"type": "integer"},
                "opensMinute": {"type": "integer"},
                "closesMinute": {"type": "integer"},
                "timeZone": {"type": "keyword"}
              }
            },
            "location": {"type": "geo_point"}
          }
        }
      }
      """;
  private static final String RESTAURANT_OPENING_HOURS_MAPPING = """
      {
        "properties": {
          "openingHoursProvided": {"type": "boolean"},
          "openingPeriods": {
            "type": "nested",
            "properties": {
              "dayOfWeek": {"type": "integer"},
              "opensMinute": {"type": "integer"},
              "closesMinute": {"type": "integer"},
              "timeZone": {"type": "keyword"}
            }
          }
        }
      }
      """;
  private static final String MENU_MAPPING = """
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
            "restaurantId": {"type": "long"},
            "name": {"type": "text"},
            "description": {"type": "text"},
            "category": {
              "type": "text",
              "fields": {"keyword": {"type": "keyword", "normalizer": "lowercase_normalizer"}}
            },
            "price": {"type": "scaled_float", "scaling_factor": 1000},
            "currency": {"type": "keyword"},
            "available": {"type": "boolean"}
          }
        }
      }
      """;

  @Inject
  @RestClient
  ElasticsearchClient client;
  private final ObjectMapper objectMapper;

  @Inject
  RestaurantSearchIndex(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void ensureIndex() {
    ensureIndex(INDEX, RESTAURANT_MAPPING);
    try (Response response = client.updateMapping(INDEX, RESTAURANT_OPENING_HOURS_MAPPING)) {
      if (response.getStatus() != 200) {
        throw failed("update opening-hours mapping in " + INDEX, response);
      }
    }
  }

  public void ensureMenuIndex() {
    ensureIndex(MENU_INDEX, MENU_MAPPING);
  }

  private void ensureIndex(String index, String mapping) {
    try (Response response = client.indexExists(index)) {
      if (response.getStatus() == 200) {
        return;
      }
      if (response.getStatus() != 404) {
        throw failed("check index " + index, response);
      }
    }
    try (Response response = client.createIndex(index, mapping)) {
      if (response.getStatus() != 200 && response.getStatus() != 201) {
        throw failed("create index " + index, response);
      }
    }
  }

  public void bulk(String index, String operations, int expectedItems) {
    try (Response response = client.bulk(index, operations)) {
      if (response.getStatus() != 200) {
        throw failed("bulk index documents in " + index, response);
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
        var result = item.properties().iterator().next();
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

  public Response searchMenuItems(String query) {
    return client.search(MENU_INDEX, query);
  }

  private IllegalStateException failed(String operation, Response response) {
    String details = response.hasEntity() ? response.readEntity(String.class) : "";
    return new IllegalStateException(
        "Elasticsearch could not " + operation + " (HTTP " + response.getStatus() + "): " + details);
  }
}
