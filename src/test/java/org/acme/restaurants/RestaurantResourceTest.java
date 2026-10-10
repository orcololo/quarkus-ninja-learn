package org.acme.restaurants;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.time.LocalDate;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.security.TestSecurity;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(RestaurantSearchTestResource.class)
class RestaurantResourceTest {
  @Inject
  EntityManager entityManager;

  @Inject
  RestaurantSearchOutboxWorker outboxWorker;

  @Inject
  ExchangeRateSnapshotRepository exchangeRateSnapshots;

  @BeforeEach
  void resetSearchIndex() {
    RestaurantSearchTestResource.reset();
    QuarkusTransaction.requiringNew().run(() -> exchangeRateSnapshots.deleteAll());
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void restaurantWritesAreIndexedAndGeoSearchUsesTheSearchBackend() {
    String city = "Geo City " + System.nanoTime();
    int nearestId = createRestaurant("Geo Pizza", city, -23.55, -46.63);
    int nearbyId = createRestaurant("Geo Pizza Nearby", city, -23.5545, -46.63);
    createRestaurant("Geo Pizza Outside", city, -23.62, -46.63);
    org.junit.jupiter.api.Assertions.assertEquals(
        "mission-admin",
        entityManager.find(RestaurantEntity.class, (long) nearestId).getOwnerSubject());

    given()
        .when()
        .get("/restaurants/{id}", nearestId)
        .then()
        .statusCode(200)
        .body("name", equalTo("Geo Pizza"))
        .body("cuisine", equalTo("ITALIAN"))
        .body("averageRating", equalTo(0.0f))
        .body("reviewCount", equalTo(0));

    outboxWorker.processPendingEvents();
    org.junit.jupiter.api.Assertions.assertEquals(0, outboxCount());
    RestaurantSearchTestResource.refresh();

    given()
        .queryParam("q", "Geo Pizza")
        .queryParam("city", city)
        .queryParam("cuisine", "ITALIAN")
        .queryParam("latitude", -23.55)
        .queryParam("longitude", -46.63)
        .queryParam("radiusKm", 5)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("restaurants", hasSize(2))
        .body("restaurants[0].id", equalTo(nearestId))
        .body("restaurants[1].id", equalTo(nearbyId))
        .body("total", equalTo(2));
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void openingHoursRoundTripAndOpenNowSearchUseTheRestaurantTimezone() {
    String city = "Hours City " + System.nanoTime();
    ZoneId zone = ZoneId.of("America/New_York");
    ZonedDateTime now = ZonedDateTime.now(zone);
    ZonedDateTime opens = now.minusMinutes(5);
    ZonedDateTime closes = now.plusMinutes(5);
    String openHours = """
        {"timeZone":"%s","periods":[{"dayOfWeek":"%s","opensAt":"%02d:%02d","closesAt":"%02d:%02d"}]}
        """.formatted(
            zone.getId(),
            opens.getDayOfWeek(),
            opens.getHour(),
            opens.getMinute(),
            closes.getHour(),
            closes.getMinute());
    int openId = createRestaurantWithHours("Open Restaurant", city, openHours);
    int closedId = createRestaurantWithHours(
        "Closed Restaurant", city, """
            {"timeZone":"America/New_York","periods":[]}
            """);
    createRestaurant("Hours Unknown", city, 0, 0);

    given()
        .when()
        .get("/restaurants/{id}", openId)
        .then()
        .statusCode(200)
        .body("openingHours.timeZone", equalTo(zone.getId()))
        .body("openingHours.periods", hasSize(1));

    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("city", city)
        .queryParam("openNow", true)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("restaurants[0].id", equalTo(openId));

    given()
        .queryParam("city", city)
        .queryParam("openNow", false)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("restaurants[0].id", equalTo(closedId));

    given()
        .contentType("application/json")
        .body("""
            {
              "name":"Open Restaurant",
              "address":"1 Test Street",
              "city":"%s",
              "cuisine":"ITALIAN",
              "latitude":0,
              "longitude":0,
              "openingHours":{"timeZone":"America/New_York","periods":[]}
            }
            """.formatted(city))
        .when()
        .put("/restaurants/{id}", openId)
        .then()
        .statusCode(200)
        .body("openingHours.periods", hasSize(0));

    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("city", city)
        .queryParam("openNow", true)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(0));
    given()
        .queryParam("city", city)
        .queryParam("openNow", false)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(2));
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void openingHoursRejectInvalidZonesAndOverlappingPeriods() {
    String body = """
        {
          "name":"Invalid Schedule",
          "address":"1 Test Street",
          "city":"Validation City",
          "cuisine":"ITALIAN",
          "latitude":0,
          "longitude":0,
          "openingHours":{
            "timeZone":"Not/A_Zone",
            "periods":[]
          }
        }
        """;
    given()
        .contentType("application/json")
        .body(body)
        .when()
        .post("/restaurants")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body("""
            {
              "name":"Overlapping Schedule",
              "address":"1 Test Street",
              "city":"Validation City",
              "cuisine":"ITALIAN",
              "latitude":0,
              "longitude":0,
              "openingHours":{
                "timeZone":"UTC",
                "periods":[
                  {"dayOfWeek":"MONDAY","opensAt":"22:00","closesAt":"02:00"},
                  {"dayOfWeek":"TUESDAY","opensAt":"01:00","closesAt":"03:00"}
                ]
              }
            }
            """)
        .when()
        .post("/restaurants")
        .then()
        .statusCode(400);
  }

  @Test
  void searchReturnsExactTotalsAboveElasticsearchDefaultLimit() {
    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.indexRestaurants(10_001);

    given()
        .queryParam("q", "threshold")
        .queryParam("size", 1)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(10_001));
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void restaurantUpdatesAndDeletesPropagateThroughTheOutbox() {
    String originalCity = "Original City " + System.nanoTime();
    String updatedCity = "Updated City " + System.nanoTime();
    int id = createRestaurant("Change Restaurant", originalCity, 5, 6);
    String body = """
        {
          "name": "Changed Restaurant",
          "address": "2 Test Street",
          "city": "%s",
          "cuisine": "THAI",
          "latitude": 5,
          "longitude": 6
        }
        """.formatted(updatedCity);

    given()
        .contentType("application/json")
        .body(body)
        .when()
        .put("/restaurants/{id}", id)
        .then()
        .statusCode(200)
        .body("name", equalTo("Changed Restaurant"));

    QuarkusTransaction.requiringNew().run(() -> {
      RestaurantSearchOutboxEntity staleEvent = new RestaurantSearchOutboxEntity();
      staleEvent.setAggregateType(RestaurantSearchAggregateType.RESTAURANT);
      staleEvent.setAggregateId((long) id);
      entityManager.persist(staleEvent);
    });

    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("city", updatedCity)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(1));
    given()
        .queryParam("city", originalCity)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(0));

    given()
        .when()
        .delete("/restaurants/{id}", id)
        .then()
        .statusCode(204);
    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("city", updatedCity)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(0));
    given()
        .when()
        .get("/restaurants/{id}", id)
        .then()
        .statusCode(404);
  }

  @Test
  @TestSecurity(user = "reviewer")
  void authenticatedUsersCanReviewOnceAndReviewUpdatesSearchData() {
    int id = createRestaurantDirectly();
    QuarkusTransaction.requiringNew().run(() -> {
      RestaurantReviewEntity existingReview = new RestaurantReviewEntity();
      existingReview.setRestaurantId((long) id);
      existingReview.setReviewerSubject("existing-reviewer");
      existingReview.setRating(3);
      existingReview.setCreatedAt(java.time.Instant.now());
      entityManager.persist(existingReview);
      entityManager.createNativeQuery(
          "update restaurant set average_rating = 3, review_count = 1 where id = ?1")
          .setParameter(1, id)
          .executeUpdate();
    });

    given()
        .contentType("application/json")
        .body("""
            {"rating":5,"comment":"Excellent"}
            """)
        .when()
        .post("/restaurants/{id}/reviews", id)
        .then()
        .statusCode(201)
        .header("Location", notNullValue())
        .body("rating", equalTo(5))
        .body("comment", equalTo("Excellent"));

    given()
        .contentType("application/json")
        .body("""
            {"rating":4,"comment":"Duplicate"}
            """)
        .when()
        .post("/restaurants/{id}/reviews", id)
        .then()
        .statusCode(409);

    given()
        .when()
        .get("/restaurants/{id}/reviews", id)
        .then()
        .statusCode(200)
        .body("reviews", hasSize(2))
        .body("total", equalTo(2));

    given()
        .when()
        .get("/restaurants/{id}", id)
        .then()
        .statusCode(200)
        .body("averageRating", equalTo(4.0f))
        .body("reviewCount", equalTo(2));
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void reindexQueuesRestaurantsInBoundedPages() {
    int added = 501;
    long outboxCountBefore = outboxCount();
    QuarkusTransaction.requiringNew().run(() -> {
      for (int index = 0; index < added; index++) {
        RestaurantEntity restaurant = new RestaurantEntity();
        restaurant.setName("Reindex Restaurant " + index);
        restaurant.setAddress("1 Test Street");
        restaurant.setCity("Reindex City");
        restaurant.setCuisine(CuisineType.ITALIAN);
        restaurant.setLatitude(0);
        restaurant.setLongitude(0);
        entityManager.persist(restaurant);
      }
    });
    long restaurantCount = entityManager.createQuery(
        "select count(r) from RestaurantEntity r", Long.class).getSingleResult();
    long menuItemCount = entityManager.createQuery(
        "select count(i) from MenuItemEntity i", Long.class).getSingleResult();

    int jobId = given()
        .when()
        .post("/restaurants/reindex")
        .then()
        .statusCode(202)
        .header("Location", notNullValue())
        .body("restaurantsQueued", equalTo(0))
        .body("menuItemsQueued", equalTo(0))
        .body("complete", equalTo(false))
        .extract()
        .path("id");

    org.junit.jupiter.api.Assertions.assertEquals(outboxCountBefore, outboxCount());
    outboxWorker.processPendingEvents();
    given()
        .when()
        .get("/restaurants/reindex/{jobId}", jobId)
        .then()
        .statusCode(200)
        .body("restaurantsQueued", equalTo(500))
        .body("complete", equalTo(false));

    for (int attempt = 0; attempt < 10; attempt++) {
      outboxWorker.processPendingEvents();
    }
    given()
        .when()
        .get("/restaurants/reindex/{jobId}", jobId)
        .then()
        .statusCode(200)
        .body("restaurantsQueued", equalTo((int) restaurantCount))
        .body("menuItemsQueued", equalTo((int) menuItemCount))
        .body("complete", equalTo(true));
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void deletingBeforeIndexingIsAcknowledgedByBulkWorker() {
    long outboxCountBefore = outboxCount();
    int id = createRestaurant("Never Indexed", "Delete City", 0, 0);
    given()
        .when()
        .delete("/restaurants/{id}", id)
        .then()
        .statusCode(204);

    outboxWorker.processPendingEvents();

    org.junit.jupiter.api.Assertions.assertEquals(outboxCountBefore, outboxCount());
  }

  @Test
  @TestSecurity(user = "restaurant-owner", roles = "ninja")
  void ownersCanDeleteTheirRestaurantAndMenuItemsButNotAnotherUsers() {
    int ownedRestaurantId = createRestaurantDirectly("restaurant-owner");
    int otherRestaurantId = createRestaurantDirectly("another-owner");

    given()
        .when()
        .delete("/restaurants/{id}", otherRestaurantId)
        .then()
        .statusCode(403);
    given()
        .when()
        .get("/restaurants/{id}", otherRestaurantId)
        .then()
        .statusCode(200);

    int ownedItemId = createMenuItemDirectly(ownedRestaurantId, "restaurant-owner");
    int otherItemId = createMenuItemDirectly(otherRestaurantId, "another-owner");
    given()
        .when()
        .delete("/restaurants/{id}/menu-items/{itemId}", otherRestaurantId, ownedItemId)
        .then()
        .statusCode(404);
    given()
        .when()
        .delete("/restaurants/{id}/menu-items/{itemId}", otherRestaurantId, otherItemId)
        .then()
        .statusCode(403);
    given()
        .when()
        .get("/restaurants/{id}/menu-items/{itemId}", otherRestaurantId, otherItemId)
        .then()
        .statusCode(200);
    given()
        .when()
        .delete("/restaurants/{id}/menu-items/{itemId}", ownedRestaurantId, ownedItemId)
        .then()
        .statusCode(204);
    given()
        .when()
        .delete("/restaurants/{id}", ownedRestaurantId)
        .then()
        .statusCode(204);
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void administratorsCanDeleteLegacyResourcesWithoutOwners() {
    int restaurantId = createRestaurantDirectly();
    int itemId = createMenuItemDirectly(restaurantId, null);

    given()
        .when()
        .delete("/restaurants/{id}/menu-items/{itemId}", restaurantId, itemId)
        .then()
        .statusCode(204);
    given()
        .when()
        .delete("/restaurants/{id}", restaurantId)
        .then()
        .statusCode(204);
  }

  @Test
  void deletingResourcesRequiresAuthentication() {
    int restaurantId = createRestaurantDirectly("anonymous-owner");
    given()
        .when()
        .delete("/restaurants/{id}", restaurantId)
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void administratorsCanManageMenuItems() {
    int restaurantId = createRestaurant("Menu Restaurant", "Menu City", 1, 2);
    String itemBody = """
        {"name":"Shoyu Ramen","description":"Pork broth ramen","category":"Mains",
         "price":14.5,"currency":"USD","available":true}
        """;

    int itemId = given()
        .contentType("application/json")
        .body(itemBody)
        .when()
        .post("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(201)
        .body("restaurantId", equalTo(restaurantId))
        .body("currency", equalTo("USD"))
        .body("available", equalTo(true))
        .extract()
        .path("id");
    org.junit.jupiter.api.Assertions.assertEquals(
        "mission-admin",
        entityManager.find(MenuItemEntity.class, (long) itemId).getOwnerSubject());

    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    saveExchangeRates();
    given()
        .queryParam("q", "Shoyu")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("results[0].item.id", equalTo(itemId))
        .body("results[0].restaurant.id", equalTo(restaurantId))
        .body("results[0].restaurant.name", equalTo("Menu Restaurant"));
    given()
        .queryParam("q", "Shoyu")
        .queryParam("currency", "BRL")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(200)
        .body("results[0].item.price", equalTo(14.5f))
        .body("results[0].item.currency", equalTo("USD"))
        .body("results[0].displayPrice.amount", equalTo(72.50f))
        .body("results[0].displayPrice.currency", equalTo("BRL"))
        .body("results[0].displayPrice.rateDate", equalTo("2026-10-10"));

    given()
        .when()
        .get("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].name", equalTo("Shoyu Ramen"))
        .body("total", equalTo(1));

    given()
        .contentType("application/json")
        .body("""
            {"name":"Vegan Shoyu Ramen","description":"Plant-based broth","category":"Mains",
             "price":15.25,"currency":"CAD","available":false}
            """)
        .when()
        .put("/restaurants/{id}/menu-items/{itemId}", restaurantId, itemId)
        .then()
        .statusCode(200)
        .body("name", equalTo("Vegan Shoyu Ramen"))
        .body("price", equalTo(15.25f))
        .body("currency", equalTo("CAD"))
        .body("available", equalTo(false));

    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("q", "Vegan")
        .queryParam("available", false)
        .queryParam("currency", "BRL")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(1))
        .body("results[0].restaurant.city", equalTo("Menu City"))
        .body("results[0].displayPrice.amount", equalTo(61.00f))
        .body("results[0].displayPrice.currency", equalTo("BRL"));

    given()
        .when()
        .delete("/restaurants/{id}/menu-items/{itemId}", restaurantId, itemId)
        .then()
        .statusCode(204);

    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("q", "Vegan")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(0));

    given()
        .when()
        .get("/restaurants/{id}/menu-items/{itemId}", restaurantId, itemId)
        .then()
        .statusCode(404);

    given()
        .contentType("application/json")
        .body("""
            {"name":"Invalid currency","price":1,"currency":"ZZZ","available":true}
            """)
        .when()
        .post("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(400);
  }

  @Test
  void menuItemsArePublicAndScopedToTheirRestaurant() {
    int restaurantId = createRestaurantDirectly();
    int otherRestaurantId = createRestaurantDirectly();
    int itemId = QuarkusTransaction.requiringNew().call(() -> {
      MenuItemEntity item = new MenuItemEntity();
      item.setRestaurantId((long) restaurantId);
      item.setName("Public Bento");
      item.setCategory("Lunch");
      item.setPrice(new java.math.BigDecimal("12.500"));
      item.setCurrency("USD");
      item.setAvailable(true);
      entityManager.persist(item);
      entityManager.flush();
      return item.getId().intValue();
    });

    given()
        .when()
        .get("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].name", equalTo("Public Bento"));

    given()
        .when()
        .get("/restaurants/{id}/menu-items/{itemId}", otherRestaurantId, itemId)
        .then()
        .statusCode(404);

    given()
        .contentType("application/json")
        .body("""
            {"name":"No permission","price":1,"currency":"USD","available":true}
            """)
        .when()
        .post("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(401);

    given()
        .when()
        .get("/restaurants/{id}/menu-items", 999_999)
        .then()
        .statusCode(404);

    given()
        .queryParam("size", 101)
        .when()
        .get("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(400);
  }

  @Test
  @TestSecurity(user = "reviewer")
  void menuMutationsRequireTheAdministratorRole() {
    int restaurantId = createRestaurantDirectly();

    given()
        .contentType("application/json")
        .body("""
            {"name":"Not allowed","price":1,"currency":"USD","available":true}
            """)
        .when()
        .post("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(403);
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void deletingRestaurantRemovesItsIndexedMenuItems() {
    int restaurantId = createRestaurant("Cascade Menu Restaurant", "Cascade City", 1, 2);
    given()
        .contentType("application/json")
        .body("""
            {"name":"Cascade Special","price":8.5,"currency":"USD","available":true}
            """)
        .when()
        .post("/restaurants/{id}/menu-items", restaurantId)
        .then()
        .statusCode(201);
    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();
    given()
        .queryParam("q", "Cascade Special")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(1));

    given()
        .when()
        .delete("/restaurants/{id}", restaurantId)
        .then()
        .statusCode(204);
    outboxWorker.processPendingEvents();
    RestaurantSearchTestResource.refresh();

    given()
        .queryParam("q", "Cascade Special")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(200)
        .body("total", equalTo(0));
  }

  @Test
  void menuItemSearchRequiresAFilter() {
    given()
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(400);

    given()
        .queryParam("q", "anything")
        .queryParam("currency", "BRL")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(503);

    given()
        .queryParam("q", "anything")
        .queryParam("currency", "ZZZ")
        .when()
        .get("/restaurants/menu-items/search")
        .then()
        .statusCode(400);
  }

  @Test
  void restaurantWritesRequireAnAdministratorAndReviewsRequireAuthentication() {
    given()
        .contentType("application/json")
        .body(validRestaurant("Unauthenticated Restaurant"))
        .when()
        .post("/restaurants")
        .then()
        .statusCode(401);

    given()
        .contentType("application/json")
        .body("""
            {"rating":5}
            """)
        .when()
        .post("/restaurants/1/reviews")
        .then()
        .statusCode(401);

    given()
        .when()
        .get("/restaurants/reindex/1")
        .then()
        .statusCode(401);
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void validatesRestaurantCoordinatesAndReviewRating() {
    given()
        .contentType("application/json")
        .body("""
            {"name":"Invalid coordinates","address":"Here","city":"There","cuisine":"AMERICAN",
             "latitude":91,"longitude":0}
            """)
        .when()
        .post("/restaurants")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body("""
            {"name":"Invalid cuisine","address":"Here","city":"There","cuisine":"NOT_A_CUISINE",
             "latitude":0,"longitude":0}
            """)
        .when()
        .post("/restaurants")
        .then()
        .statusCode(400);

    int id = createRestaurant("Validated Restaurant", "Validation City", 0, 0);
    given()
        .queryParam("latitude", 0)
        .queryParam("longitude", 0)
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(400);

    given()
        .queryParam("cuisine", "NOT_A_CUISINE")
        .when()
        .get("/restaurants/search")
        .then()
        .statusCode(400);

    given()
        .contentType("application/json")
        .body("""
            {"rating":6}
            """)
        .when()
        .post("/restaurants/{id}/reviews", id)
        .then()
        .statusCode(400);
  }

  private int createRestaurant(String name, String city, double latitude, double longitude) {
    return given()
        .contentType("application/json")
        .body("""
            {
              "name": "%s",
              "description": "Restaurant resource test",
              "address": "1 Test Street",
              "city": "%s",
              "cuisine": "ITALIAN",
              "latitude": %s,
              "longitude": %s
            }
            """.formatted(name, city, latitude, longitude))
        .when()
        .post("/restaurants")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }

  private int createRestaurantWithHours(String name, String city, String openingHours) {
    return given()
        .contentType("application/json")
        .body("""
            {
              "name":"%s",
              "address":"1 Test Street",
              "city":"%s",
              "cuisine":"ITALIAN",
              "latitude":0,
              "longitude":0,
              "openingHours":%s
            }
            """.formatted(name, city, openingHours))
        .when()
        .post("/restaurants")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }

  private int createRestaurantDirectly() {
    return createRestaurantDirectly(null);
  }

  private int createRestaurantDirectly(String ownerSubject) {
    return QuarkusTransaction.requiringNew().call(() -> {
      RestaurantEntity restaurant = new RestaurantEntity();
      restaurant.setName("Review Restaurant");
      restaurant.setAddress("1 Test Street");
      restaurant.setCity("Review City " + System.nanoTime());
      restaurant.setCuisine(CuisineType.ITALIAN);
      restaurant.setLatitude(10);
      restaurant.setLongitude(20);
      restaurant.setOwnerSubject(ownerSubject);
      entityManager.persist(restaurant);
      entityManager.flush();
      return restaurant.getId().intValue();
    });
  }

  private int createMenuItemDirectly(int restaurantId, String ownerSubject) {
    return QuarkusTransaction.requiringNew().call(() -> {
      MenuItemEntity item = new MenuItemEntity();
      item.setRestaurantId((long) restaurantId);
      item.setName("Owner Test Item");
      item.setPrice(new java.math.BigDecimal("12.500"));
      item.setCurrency("USD");
      item.setAvailable(true);
      item.setOwnerSubject(ownerSubject);
      entityManager.persist(item);
      entityManager.flush();
      return item.getId().intValue();
    });
  }

  private String validRestaurant(String name) {
    return """
        {"name":"%s","address":"1 Test Street","city":"Test City","cuisine":"ITALIAN",
         "latitude":0,"longitude":0}
        """.formatted(name);
  }

  private long outboxCount() {
    return entityManager.createQuery("select count(e) from RestaurantSearchOutboxEntity e", Long.class)
        .getSingleResult();
  }

  private void saveExchangeRates() {
    QuarkusTransaction.requiringNew().run(() -> {
      ExchangeRateSnapshotEntity snapshot = new ExchangeRateSnapshotEntity();
      snapshot.setId(1L);
      snapshot.setRateDate(LocalDate.of(2026, 10, 10));
      snapshot.setFetchedAt(Instant.now());
      snapshot.setRatesJson("""
          {"usd":1.0,"brl":5.0,"cad":1.25}
          """);
      exchangeRateSnapshots.persist(snapshot);
    });
  }
}
