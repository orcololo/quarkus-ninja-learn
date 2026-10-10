package org.acme.restaurants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.quarkus.narayana.jta.QuarkusTransaction;
import org.acme.restaurants.dto.CreateRestaurantRequest;
import org.acme.restaurants.dto.CreateRestaurantReviewRequest;
import org.acme.restaurants.dto.RestaurantResponseDto;
import org.acme.restaurants.dto.RestaurantReviewResponseDto;
import org.acme.restaurants.dto.RestaurantReviewPageResponseDto;
import org.acme.restaurants.dto.RestaurantSearchPageResponseDto;
import org.acme.restaurants.dto.UpdateRestaurantRequest;
import org.acme.restaurants.mapper.RestaurantMapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RestaurantService {
  private final RestaurantRepository restaurants;
  private final RestaurantReviewRepository reviews;
  private final RestaurantMapper mapper;
  private final RestaurantSearchIndex searchIndex;
  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;
  private final SecurityIdentity identity;
  RestaurantService(
      RestaurantRepository restaurants,
      RestaurantReviewRepository reviews,
      RestaurantMapper mapper,
      RestaurantSearchIndex searchIndex,
      EntityManager entityManager,
      ObjectMapper objectMapper,
      SecurityIdentity identity) {
    this.restaurants = restaurants;
    this.reviews = reviews;
    this.mapper = mapper;
    this.searchIndex = searchIndex;
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
    this.identity = identity;
  }

  @Transactional
  public RestaurantResponseDto create(CreateRestaurantRequest request) {
    RestaurantEntity restaurant = new RestaurantEntity();
    copy(request.name(), request.description(), request.address(), request.city(),
        request.cuisine(), request.latitude(), request.longitude(), restaurant);
    restaurants.persist(restaurant);
    entityManager.flush();
    enqueueSearchChange(restaurant.getId());
    return mapper.toResponse(restaurant);
  }

  public RestaurantResponseDto get(long id) {
    return mapper.toResponse(findRestaurant(id));
  }

  @Transactional
  public RestaurantResponseDto update(long id, UpdateRestaurantRequest request) {
    RestaurantEntity restaurant = findRestaurant(id, LockModeType.PESSIMISTIC_WRITE);
    copy(request.name(), request.description(), request.address(), request.city(),
        request.cuisine(), request.latitude(), request.longitude(), restaurant);
    entityManager.flush();
    entityManager.refresh(restaurant);
    enqueueSearchChange(restaurant.getId());
    return mapper.toResponse(restaurant);
  }

  @Transactional
  public void delete(long id) {
    RestaurantEntity restaurant = findRestaurant(id, LockModeType.PESSIMISTIC_WRITE);
    restaurants.delete(restaurant);
    enqueueSearchChange(id);
  }

  public RestaurantReviewPageResponseDto listReviews(long restaurantId, int page, int size) {
    findRestaurant(restaurantId);
    List<RestaurantReviewResponseDto> result = reviews.listForRestaurant(restaurantId, page, size).stream()
        .map(mapper::toResponse)
        .toList();
    return new RestaurantReviewPageResponseDto(
        result, page, size, reviews.countForRestaurant(restaurantId));
  }

  public RestaurantReviewResponseDto getReview(long restaurantId, long reviewId) {
    findRestaurant(restaurantId);
    RestaurantReviewEntity review = reviews.findForRestaurant(restaurantId, reviewId);
    if (review == null) {
      throw new NotFoundException("Review not found: " + reviewId);
    }
    return mapper.toResponse(review);
  }

  @Transactional
  public RestaurantReviewResponseDto createReview(
      long restaurantId, CreateRestaurantReviewRequest request) {
    RestaurantEntity restaurant = findRestaurant(restaurantId, LockModeType.PESSIMISTIC_WRITE);
    String subject = identity.getPrincipal().getName();
    if (reviews.count("restaurantId = ?1 and reviewerSubject = ?2", restaurantId, subject) > 0) {
      throw new jakarta.ws.rs.ClientErrorException("You have already reviewed this restaurant", 409);
    }

    RestaurantReviewEntity review = new RestaurantReviewEntity();
    review.setRestaurantId(restaurantId);
    review.setReviewerSubject(subject);
    review.setRating(request.rating());
    review.setComment(request.comment());
    review.setCreatedAt(java.time.Instant.now());
    reviews.persist(review);
    entityManager.flush();
    entityManager.createNativeQuery("""
        update restaurant
        set average_rating = (average_rating * review_count + ?1) / (review_count + 1),
            review_count = review_count + 1
        where id = ?2
        """)
        .setParameter(1, request.rating())
        .setParameter(2, restaurantId)
        .executeUpdate();
    entityManager.refresh(restaurant);
    enqueueSearchChange(restaurantId);
    return mapper.toResponse(review);
  }

  public RestaurantSearchPageResponseDto search(
      String query,
      String city,
      String cuisine,
      Double latitude,
      Double longitude,
      Double radiusKm,
      int page,
      int size) {
    boolean anyCoordinates = latitude != null || longitude != null || radiusKm != null;
    boolean allCoordinates = latitude != null && longitude != null && radiusKm != null;
    if (anyCoordinates != allCoordinates) {
      throw new BadRequestException("latitude, longitude, and radiusKm must be provided together");
    }
    if (!anyCoordinates && blank(query) && blank(city) && blank(cuisine)) {
      throw new BadRequestException("Provide search text, city, cuisine, or geo coordinates");
    }

    String body = buildSearchQuery(query, city, cuisine, latitude, longitude, radiusKm, page, size);
    try (var response = searchIndex.search(body)) {
      if (response.getStatus() != 200) {
        throw new ServiceUnavailableException(
            "Restaurant search is temporarily unavailable (Elasticsearch HTTP "
                + response.getStatus() + ")");
      }
      return readSearchResponse(response.readEntity(String.class), page, size);
    } catch (org.jboss.resteasy.reactive.ClientWebApplicationException exception) {
      throw unavailable(exception);
    } catch (jakarta.ws.rs.ProcessingException exception) {
      throw unavailable(exception);
    }
  }

  public int queueReindex() {
    int queued = 0;
    long afterId = 0;
    while (true) {
      long batchAfterId = afterId;
      ReindexBatch batch = QuarkusTransaction.requiringNew()
          .call(() -> queueReindexBatch(batchAfterId));
      if (batch.count() == 0) {
        return queued;
      }
      queued += batch.count();
      afterId = batch.lastId();
    }
  }

  private ReindexBatch queueReindexBatch(long afterId) {
    List<RestaurantEntity> batch = restaurants.findAfterId(afterId, 500);
    batch.forEach(restaurant -> enqueueSearchChange(restaurant.getId()));
    return batch.isEmpty()
        ? new ReindexBatch(0, afterId)
        : new ReindexBatch(batch.size(), batch.getLast().getId());
  }

  private String buildSearchQuery(
      String query,
      String city,
      String cuisine,
      Double latitude,
      Double longitude,
      Double radiusKm,
      int page,
      int size) {
    Map<String, Object> bool = new HashMap<>();
    List<Map<String, Object>> filters = new ArrayList<>();
    List<Map<String, Object>> must = new ArrayList<>();
    if (!blank(query)) {
      must.add(Map.of("multi_match", Map.of(
          "query", query.trim(),
          "fields", List.of("name^3", "cuisine^2", "description", "address", "city"))));
    }
    if (!blank(city)) {
      filters.add(Map.of("term", Map.of("city.keyword", city.trim())));
    }
    if (!blank(cuisine)) {
      filters.add(Map.of("term", Map.of("cuisine.keyword", cuisine.trim())));
    }
    if (latitude != null) {
      filters.add(Map.of("geo_distance", Map.of(
          "distance", radiusKm + "km",
          "location", Map.of("lat", latitude, "lon", longitude))));
    }
    if (!must.isEmpty()) {
      bool.put("must", must);
    }
    if (!filters.isEmpty()) {
      bool.put("filter", filters);
    }

    List<Map<String, Object>> sort = latitude != null
        ? List.of(Map.of("_geo_distance", Map.of(
            "location", Map.of("lat", latitude, "lon", longitude),
            "order", "asc",
            "unit", "km")),
            Map.of("id", Map.of("order", "asc")))
        : List.of(Map.of("_score", Map.of("order", "desc")),
            Map.of("id", Map.of("order", "asc")));
    Map<String, Object> request = Map.of(
        "from", page * size,
        "size", size,
        "track_total_hits", true,
        "query", Map.of("bool", bool),
        "sort", sort);
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize restaurant search query", exception);
    }
  }

  private RestaurantSearchPageResponseDto readSearchResponse(String json, int page, int size) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode totalNode = root.path("hits").path("total").path("value");
      JsonNode hits = root.path("hits").path("hits");
      if (!totalNode.isNumber() || !hits.isArray()) {
        throw new ServiceUnavailableException("Elasticsearch returned an invalid search response");
      }
      long total = totalNode.asLong();
      List<Long> ids = new ArrayList<>();
      for (JsonNode hit : hits) {
        String id = hit.path("_id").asText(null);
        if (id == null) {
          throw new ServiceUnavailableException("Elasticsearch returned a hit without an id");
        }
        ids.add(Long.parseLong(id));
      }
      if (ids.isEmpty()) {
        return new RestaurantSearchPageResponseDto(List.of(), page, size, total);
      }
      Map<Long, RestaurantEntity> byId = new HashMap<>();
      restaurants.findRestaurantsByIds(ids).forEach(restaurant -> byId.put(restaurant.getId(), restaurant));
      List<RestaurantResponseDto> results = ids.stream()
          .map(byId::get)
          .filter(restaurant -> restaurant != null)
          .map(mapper::toResponse)
          .toList();
      return new RestaurantSearchPageResponseDto(results, page, size, total);
    } catch (JsonProcessingException | NumberFormatException exception) {
      throw new ServiceUnavailableException(
          Response.status(503).entity("Elasticsearch returned an invalid search response").build(),
          exception);
    }
  }

  private ServiceUnavailableException unavailable(Throwable cause) {
    return new ServiceUnavailableException(
        Response.status(503).entity("Restaurant search is temporarily unavailable").build(), cause);
  }

  private void enqueueSearchChange(long restaurantId) {
    RestaurantSearchOutboxEntity event = new RestaurantSearchOutboxEntity();
    event.setRestaurantId(restaurantId);
    entityManager.persist(event);
  }

  private RestaurantEntity findRestaurant(long id) {
    return findRestaurant(id, LockModeType.NONE);
  }

  private RestaurantEntity findRestaurant(long id, LockModeType lockMode) {
    RestaurantEntity restaurant = restaurants.findById(id, lockMode);
    if (restaurant == null) {
      throw new NotFoundException("Restaurant not found: " + id);
    }
    return restaurant;
  }

  private void copy(
      String name,
      String description,
      String address,
      String city,
      String cuisine,
      double latitude,
      double longitude,
      RestaurantEntity restaurant) {
    restaurant.setName(name);
    restaurant.setDescription(description);
    restaurant.setAddress(address);
    restaurant.setCity(city);
    restaurant.setCuisine(cuisine);
    restaurant.setLatitude(latitude);
    restaurant.setLongitude(longitude);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private record ReindexBatch(int count, long lastId) {
  }
}
