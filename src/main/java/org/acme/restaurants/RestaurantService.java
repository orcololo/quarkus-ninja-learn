package org.acme.restaurants;

import java.util.Currency;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.acme.dto.PageResponseDto;
import org.acme.restaurants.dto.CreateRestaurantRequest;
import org.acme.restaurants.dto.CreateRestaurantReviewRequest;
import org.acme.restaurants.dto.DisplayPriceDto;
import org.acme.restaurants.dto.MenuItemRequest;
import org.acme.restaurants.dto.MenuItemResponseDto;
import org.acme.restaurants.dto.MenuItemSearchPageResponseDto;
import org.acme.restaurants.dto.MenuItemSearchResultDto;
import org.acme.restaurants.dto.OpeningHoursDto;
import org.acme.restaurants.dto.OpeningPeriodDto;
import org.acme.restaurants.dto.RestaurantResponseDto;
import org.acme.restaurants.dto.RestaurantReviewResponseDto;
import org.acme.restaurants.dto.RestaurantReviewPageResponseDto;
import org.acme.restaurants.dto.RestaurantReindexResponseDto;
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
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class RestaurantService {
  private static final String OPEN_NOW_SCRIPT = """
      def local = java.time.Instant.ofEpochMilli(params.now)
          .atZone(java.time.ZoneId.of(doc['openingPeriods.timeZone'].value));
      int currentDay = local.getDayOfWeek().getValue();
      int currentMinute = local.getHour() * 60 + local.getMinute();
      long startDay = doc['openingPeriods.dayOfWeek'].value;
      long opens = doc['openingPeriods.opensMinute'].value;
      long closes = doc['openingPeriods.closesMinute'].value;
      if (currentDay == startDay) {
        return closes > opens
            ? currentMinute >= opens && currentMinute < closes
            : currentMinute >= opens;
      }
      return closes < opens
          && currentDay == (startDay % 7) + 1
          && currentMinute < closes;
      """;

  private final RestaurantRepository restaurants;
  private final RestaurantReviewRepository reviews;
  private final MenuItemRepository menuItems;
  private final RestaurantOpeningPeriodRepository openingPeriods;
  private final ExchangeRateService exchangeRates;
  private final RestaurantMapper mapper;
  private final RestaurantSearchIndex searchIndex;
  private final EntityManager entityManager;
  private final ObjectMapper objectMapper;
  private final SecurityIdentity identity;
  RestaurantService(
      RestaurantRepository restaurants,
      RestaurantReviewRepository reviews,
      MenuItemRepository menuItems,
      RestaurantOpeningPeriodRepository openingPeriods,
      ExchangeRateService exchangeRates,
      RestaurantMapper mapper,
      RestaurantSearchIndex searchIndex,
      EntityManager entityManager,
      ObjectMapper objectMapper,
      SecurityIdentity identity) {
    this.restaurants = restaurants;
    this.reviews = reviews;
    this.menuItems = menuItems;
    this.openingPeriods = openingPeriods;
    this.exchangeRates = exchangeRates;
    this.mapper = mapper;
    this.searchIndex = searchIndex;
    this.entityManager = entityManager;
    this.objectMapper = objectMapper;
    this.identity = identity;
  }

  @Transactional
  public RestaurantResponseDto create(CreateRestaurantRequest request) {
    RestaurantEntity restaurant = new RestaurantEntity();
    restaurant.setOwnerSubject(identity.getPrincipal().getName());
    copy(request.name(), request.description(), request.address(), request.city(),
        request.cuisine(), request.latitude(), request.longitude(), restaurant);
    restaurants.persist(restaurant);
    entityManager.flush();
    replaceOpeningHours(restaurant, request.openingHours());
    entityManager.flush();
    enqueueSearchChange(RestaurantSearchAggregateType.RESTAURANT, restaurant.getId());
    return restaurantResponse(restaurant);
  }

  public RestaurantResponseDto get(long id) {
    return restaurantResponse(findRestaurant(id));
  }

  @Transactional
  public RestaurantResponseDto update(long id, UpdateRestaurantRequest request) {
    RestaurantEntity restaurant = findRestaurant(id, LockModeType.PESSIMISTIC_WRITE);
    copy(request.name(), request.description(), request.address(), request.city(),
        request.cuisine(), request.latitude(), request.longitude(), restaurant);
    replaceOpeningHours(restaurant, request.openingHours());
    entityManager.flush();
    entityManager.refresh(restaurant);
    enqueueSearchChange(RestaurantSearchAggregateType.RESTAURANT, restaurant.getId());
    return restaurantResponse(restaurant);
  }

  @Transactional
  public void delete(long id) {
    RestaurantEntity restaurant = findRestaurant(id, LockModeType.PESSIMISTIC_WRITE);
    requireOwnerOrAdmin(restaurant.getOwnerSubject());
    menuItems.listIdsForRestaurant(id)
        .forEach(itemId -> enqueueSearchChange(RestaurantSearchAggregateType.MENU_ITEM, itemId));
    restaurants.delete(restaurant);
    enqueueSearchChange(RestaurantSearchAggregateType.RESTAURANT, id);
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

  public PageResponseDto<MenuItemResponseDto> listMenuItems(long restaurantId, int page, int size) {
    findRestaurant(restaurantId);
    List<MenuItemResponseDto> items = menuItems.listForRestaurant(restaurantId, page, size).stream()
        .map(mapper::toResponse)
        .toList();
    return new PageResponseDto<>(items, page, size, menuItems.countForRestaurant(restaurantId));
  }

  public MenuItemResponseDto getMenuItem(long restaurantId, long itemId) {
    findRestaurant(restaurantId);
    MenuItemEntity item = findMenuItem(restaurantId, itemId);
    return mapper.toResponse(item);
  }

  @Transactional
  public MenuItemResponseDto createMenuItem(long restaurantId, MenuItemRequest request) {
    findRestaurant(restaurantId, LockModeType.PESSIMISTIC_WRITE);
    validateCurrency(request.currency());
    MenuItemEntity item = new MenuItemEntity();
    item.setRestaurantId(restaurantId);
    item.setOwnerSubject(identity.getPrincipal().getName());
    copy(request, item);
    menuItems.persist(item);
    entityManager.flush();
    enqueueSearchChange(RestaurantSearchAggregateType.MENU_ITEM, item.getId());
    return mapper.toResponse(item);
  }

  @Transactional
  public MenuItemResponseDto updateMenuItem(
      long restaurantId, long itemId, MenuItemRequest request) {
    findRestaurant(restaurantId, LockModeType.PESSIMISTIC_WRITE);
    validateCurrency(request.currency());
    MenuItemEntity item = findMenuItem(restaurantId, itemId);
    copy(request, item);
    enqueueSearchChange(RestaurantSearchAggregateType.MENU_ITEM, itemId);
    return mapper.toResponse(item);
  }

  @Transactional
  public void deleteMenuItem(long restaurantId, long itemId) {
    findRestaurant(restaurantId, LockModeType.PESSIMISTIC_WRITE);
    MenuItemEntity item = findMenuItem(restaurantId, itemId);
    requireOwnerOrAdmin(item.getOwnerSubject());
    menuItems.delete(item);
    enqueueSearchChange(RestaurantSearchAggregateType.MENU_ITEM, itemId);
  }

  public MenuItemSearchPageResponseDto searchMenuItems(
      String query, String category, Boolean available, String currency, int page, int size) {
    if (blank(query) && blank(category) && available == null) {
      throw new BadRequestException("Provide menu item text, category, or availability");
    }
    ExchangeRateService.PriceConversion conversion =
        currency == null || currency.isBlank() ? null : exchangeRates.conversionTo(currency);
    String body = buildMenuItemSearchQuery(query, category, available, page, size);
    try (var response = searchIndex.searchMenuItems(body)) {
      if (response.getStatus() != 200) {
        throw new ServiceUnavailableException(
            "Menu item search is temporarily unavailable (Elasticsearch HTTP "
                + response.getStatus() + ")");
      }
      return readMenuItemSearchResponse(
          response.readEntity(String.class), page, size, conversion);
    } catch (org.jboss.resteasy.reactive.ClientWebApplicationException exception) {
      throw unavailable(exception);
    } catch (jakarta.ws.rs.ProcessingException exception) {
      throw unavailable(exception);
    }
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
    enqueueSearchChange(RestaurantSearchAggregateType.RESTAURANT, restaurantId);
    return mapper.toResponse(review);
  }

  public RestaurantSearchPageResponseDto search(
      String query,
      String city,
      String cuisine,
      Double latitude,
      Double longitude,
      Double radiusKm,
      Boolean openNow,
      int page,
      int size) {
    boolean anyCoordinates = latitude != null || longitude != null || radiusKm != null;
    boolean allCoordinates = latitude != null && longitude != null && radiusKm != null;
    if (anyCoordinates != allCoordinates) {
      throw new BadRequestException("latitude, longitude, and radiusKm must be provided together");
    }
    CuisineType cuisineType = cuisine == null || cuisine.isBlank()
        ? null
        : parseCuisine(cuisine);
    if (!anyCoordinates && blank(query) && blank(city) && cuisineType == null && openNow == null) {
      throw new BadRequestException("Provide search text, city, cuisine, geo coordinates, or openNow");
    }

    String body = buildSearchQuery(
        query, city, cuisineType, latitude, longitude, radiusKm, openNow, page, size);
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

  @Transactional
  public RestaurantReindexResponseDto queueReindex() {
    RestaurantReindexJobEntity job = new RestaurantReindexJobEntity();
    entityManager.persist(job);
    entityManager.flush();
    return reindexResponse(job);
  }

  public RestaurantReindexResponseDto getReindex(long jobId) {
    RestaurantReindexJobEntity job = entityManager.createQuery(
        "from RestaurantReindexJobEntity j where j.id = :id", RestaurantReindexJobEntity.class)
        .setParameter("id", jobId)
        .getResultStream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException("Restaurant reindex job not found: " + jobId));
    return reindexResponse(job);
  }

  private RestaurantReindexResponseDto reindexResponse(RestaurantReindexJobEntity job) {
    return new RestaurantReindexResponseDto(
        job.getId(), job.getRestaurantsQueued(), job.getMenuItemsQueued(), job.isComplete());
  }

  private String buildMenuItemSearchQuery(
      String query, String category, Boolean available, int page, int size) {
    List<Map<String, Object>> filters = new ArrayList<>();
    List<Map<String, Object>> must = new ArrayList<>();
    if (!blank(query)) {
      must.add(Map.of("multi_match", Map.of(
          "query", query.trim(),
          "fields", List.of("name^3", "description", "category"))));
    }
    if (!blank(category)) {
      filters.add(Map.of("term", Map.of("category.keyword", category.trim())));
    }
    if (available != null) {
      filters.add(Map.of("term", Map.of("available", available)));
    }
    Map<String, Object> bool = new HashMap<>();
    if (!must.isEmpty()) {
      bool.put("must", must);
    }
    if (!filters.isEmpty()) {
      bool.put("filter", filters);
    }
    Map<String, Object> request = Map.of(
        "from", page * size,
        "size", size,
        "track_total_hits", true,
        "query", Map.of("bool", bool),
        "sort", List.of(
            Map.of("_score", Map.of("order", "desc")),
            Map.of("id", Map.of("order", "asc"))));
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Could not serialize menu item search query", exception);
    }
  }

  private String buildSearchQuery(
      String query,
      String city,
      CuisineType cuisine,
      Double latitude,
      Double longitude,
      Double radiusKm,
      Boolean openNow,
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
    if (cuisine != null) {
      filters.add(Map.of("term", Map.of("cuisine.keyword", cuisine.name())));
    }
    if (latitude != null) {
      filters.add(Map.of("geo_distance", Map.of(
          "distance", radiusKm + "km",
          "location", Map.of("lat", latitude, "lon", longitude))));
    }
    if (openNow != null) {
      filters.add(Map.of("term", Map.of("openingHoursProvided", true)));
      Map<String, Object> openingStatus = Map.of(
          "nested", Map.of(
              "path", "openingPeriods",
              "query", Map.of(
                  "script", Map.of("script", Map.of(
                      "lang", "painless",
                      "source", OPEN_NOW_SCRIPT,
                      "params", Map.of("now", java.time.Instant.now().toEpochMilli()))))));
      if (openNow) {
        filters.add(openingStatus);
      } else {
        bool.put("must_not", List.of(openingStatus));
      }
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
      Map<Long, List<RestaurantOpeningPeriodEntity>> periodsByRestaurant =
          openingPeriods.listForRestaurants(ids).stream()
              .collect(java.util.stream.Collectors.groupingBy(
                  period -> period.getRestaurantId()));
      List<RestaurantResponseDto> results = ids.stream()
          .map(byId::get)
          .filter(restaurant -> restaurant != null)
          .map(restaurant -> mapper.toResponse(
              restaurant, periodsByRestaurant.getOrDefault(restaurant.getId(), List.of())))
          .toList();
      return new RestaurantSearchPageResponseDto(results, page, size, total);
    } catch (JsonProcessingException | NumberFormatException exception) {
      throw new ServiceUnavailableException(
          Response.status(503).entity("Elasticsearch returned an invalid search response").build(),
          exception);
    }
  }

  private MenuItemSearchPageResponseDto readMenuItemSearchResponse(
      String json,
      int page,
      int size,
      ExchangeRateService.PriceConversion conversion) {
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode totalNode = root.path("hits").path("total").path("value");
      JsonNode hits = root.path("hits").path("hits");
      if (!totalNode.isNumber() || !hits.isArray()) {
        throw new ServiceUnavailableException("Elasticsearch returned an invalid menu search response");
      }
      long total = totalNode.asLong();
      List<Long> ids = new ArrayList<>();
      for (JsonNode hit : hits) {
        String id = hit.path("_id").asText(null);
        if (id == null) {
          throw new ServiceUnavailableException("Elasticsearch returned a menu item without an id");
        }
        ids.add(Long.parseLong(id));
      }
      if (ids.isEmpty()) {
        return new MenuItemSearchPageResponseDto(List.of(), page, size, total);
      }
      Map<Long, MenuItemEntity> itemsById = new HashMap<>();
      menuItems.findItemsWithIds(ids).forEach(item -> itemsById.put(item.getId(), item));
      List<Long> restaurantIds = itemsById.values().stream()
          .map(item -> item.getRestaurantId())
          .distinct()
          .toList();
      Map<Long, RestaurantEntity> restaurantsById = new HashMap<>();
      restaurants.findRestaurantsByIds(restaurantIds)
          .forEach(restaurant -> restaurantsById.put(restaurant.getId(), restaurant));
      Map<Long, List<RestaurantOpeningPeriodEntity>> periodsByRestaurant =
          openingPeriods.listForRestaurants(restaurantIds).stream()
              .collect(java.util.stream.Collectors.groupingBy(
                  period -> period.getRestaurantId()));
      List<MenuItemSearchResultDto> results = new ArrayList<>();
      for (Long id : ids) {
        MenuItemEntity item = itemsById.get(id);
        if (item == null) {
          continue;
        }
        RestaurantEntity restaurant = restaurantsById.get(item.getRestaurantId());
        if (restaurant != null) {
          results.add(new MenuItemSearchResultDto(
              mapper.toResponse(item),
              mapper.toResponse(
                  restaurant,
                  periodsByRestaurant.getOrDefault(restaurant.getId(), List.of())),
              conversion == null ? null : displayPrice(conversion, item)));
        }
      }
      results = List.copyOf(results);
      return new MenuItemSearchPageResponseDto(results, page, size, total);
    } catch (JsonProcessingException | NumberFormatException exception) {
      throw new ServiceUnavailableException(
          Response.status(503).entity("Elasticsearch returned an invalid menu search response").build(),
          exception);
    }
  }

  private DisplayPriceDto displayPrice(
      ExchangeRateService.PriceConversion conversion, MenuItemEntity item) {
    ExchangeRateService.DisplayPrice price = conversion.convert(item.getPrice(), item.getCurrency());
    return new DisplayPriceDto(price.amount(), price.currency(), price.rateDate());
  }

  private ServiceUnavailableException unavailable(Throwable cause) {
    return new ServiceUnavailableException(
        Response.status(503).entity("Restaurant search is temporarily unavailable").build(), cause);
  }

  private void enqueueSearchChange(RestaurantSearchAggregateType aggregateType, long aggregateId) {
    RestaurantSearchOutboxEntity event = new RestaurantSearchOutboxEntity();
    event.setAggregateType(aggregateType);
    event.setAggregateId(aggregateId);
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

  private MenuItemEntity findMenuItem(long restaurantId, long itemId) {
    MenuItemEntity item = menuItems.findForRestaurant(restaurantId, itemId);
    if (item == null) {
      throw new NotFoundException("Menu item not found: " + itemId);
    }
    return item;
  }

  private void validateCurrency(String code) {
    try {
      Currency.getInstance(code);
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("currency must be a supported ISO 4217 code", exception);
    }
  }

  private void requireOwnerOrAdmin(String ownerSubject) {
    if (!identity.hasRole("mission-admin")
        && !identity.getPrincipal().getName().equals(ownerSubject)) {
      throw new ForbiddenException("Only the resource owner can delete it");
    }
  }

  private CuisineType parseCuisine(String value) {
    try {
      return CuisineType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new BadRequestException("cuisine must be a supported CuisineType value", exception);
    }
  }

  private void copy(MenuItemRequest request, MenuItemEntity item) {
    item.setName(request.name());
    item.setDescription(request.description());
    item.setCategory(request.category());
    item.setPrice(request.price());
    item.setCurrency(request.currency());
    item.setAvailable(request.available());
  }

  private void copy(
      String name,
      String description,
      String address,
      String city,
      CuisineType cuisine,
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

  private RestaurantResponseDto restaurantResponse(RestaurantEntity restaurant) {
    return mapper.toResponse(restaurant, openingPeriods.listForRestaurant(restaurant.getId()));
  }

  private void replaceOpeningHours(RestaurantEntity restaurant, OpeningHoursDto hours) {
    if (hours == null) {
      restaurant.setTimeZone(null);
      openingPeriods.delete("restaurantId", restaurant.getId());
      return;
    }
    if (!ZoneId.getAvailableZoneIds().contains(hours.timeZone())) {
      throw new BadRequestException("timeZone must be an IANA timezone identifier");
    }
    validateOpeningPeriods(hours.periods());
    restaurant.setTimeZone(hours.timeZone());
    openingPeriods.delete("restaurantId", restaurant.getId());
    for (OpeningPeriodDto period : hours.periods()) {
      RestaurantOpeningPeriodEntity entity = new RestaurantOpeningPeriodEntity();
      entity.setRestaurantId(restaurant.getId());
      entity.setDayOfWeek(period.dayOfWeek());
      entity.setOpensAt(period.opensAt());
      entity.setClosesAt(period.closesAt());
      openingPeriods.persist(entity);
    }
  }

  private void validateOpeningPeriods(List<OpeningPeriodDto> periods) {
    List<long[]> intervals = new ArrayList<>(periods.size() * 2);
    long weekMinutes = 7 * 24 * 60;
    for (OpeningPeriodDto period : periods) {
      LocalTime opens = period.opensAt();
      LocalTime closes = period.closesAt();
      if (opens.equals(closes)
          || opens.getSecond() != 0 || opens.getNano() != 0
          || closes.getSecond() != 0 || closes.getNano() != 0) {
        throw new BadRequestException(
            "Opening periods must have different start and end times with minute precision");
      }
      long start = (period.dayOfWeek().getValue() - 1L) * 24 * 60
          + opens.getHour() * 60L + opens.getMinute();
      long duration = (closes.toSecondOfDay() - opens.toSecondOfDay() + 24 * 60 * 60)
          % (24 * 60 * 60) / 60;
      intervals.add(new long[] {start, start + duration});
    }
    for (int left = 0; left < intervals.size(); left++) {
      for (int right = left + 1; right < intervals.size(); right++) {
        long[] first = intervals.get(left);
        long[] second = intervals.get(right);
        for (int weekShift = -1; weekShift <= 1; weekShift++) {
          long shiftedStart = second[0] + weekShift * weekMinutes;
          long shiftedEnd = second[1] + weekShift * weekMinutes;
          if (first[0] < shiftedEnd && shiftedStart < first[1]) {
            throw new BadRequestException("Opening periods must not overlap");
          }
        }
      }
    }
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

}
