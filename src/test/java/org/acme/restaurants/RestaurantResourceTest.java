package org.acme.restaurants;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.security.TestSecurity;
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

  @BeforeEach
  void resetSearchIndex() {
    RestaurantSearchTestResource.reset();
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void restaurantWritesAreIndexedAndGeoSearchUsesTheSearchBackend() {
    String city = "Geo City " + System.nanoTime();
    int nearestId = createRestaurant("Geo Pizza", city, -23.55, -46.63);
    int nearbyId = createRestaurant("Geo Pizza Nearby", city, -23.5545, -46.63);
    createRestaurant("Geo Pizza Outside", city, -23.62, -46.63);

    given()
        .when()
        .get("/restaurants/{id}", nearestId)
        .then()
        .statusCode(200)
        .body("name", equalTo("Geo Pizza"))
        .body("averageRating", equalTo(0.0f))
        .body("reviewCount", equalTo(0));

    outboxWorker.processPendingEvents();
    org.junit.jupiter.api.Assertions.assertEquals(0, outboxCount());
    RestaurantSearchTestResource.refresh();

    given()
        .queryParam("q", "Geo Pizza")
        .queryParam("city", city)
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
          "cuisine": "Thai",
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
      staleEvent.setRestaurantId((long) id);
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
        restaurant.setCuisine("Italian");
        restaurant.setLatitude(0);
        restaurant.setLongitude(0);
        entityManager.persist(restaurant);
      }
    });
    long restaurantCount = entityManager.createQuery(
        "select count(r) from RestaurantEntity r", Long.class).getSingleResult();

    given()
        .when()
        .post("/restaurants/reindex")
        .then()
        .statusCode(202)
        .body("restaurantsQueued", equalTo((int) restaurantCount));
    org.junit.jupiter.api.Assertions.assertEquals(
        outboxCountBefore + restaurantCount, outboxCount());
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
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void validatesRestaurantCoordinatesAndReviewRating() {
    given()
        .contentType("application/json")
        .body("""
            {"name":"Invalid coordinates","address":"Here","city":"There","cuisine":"Food",
             "latitude":91,"longitude":0}
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
              "cuisine": "Italian",
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

  private int createRestaurantDirectly() {
    return QuarkusTransaction.requiringNew().call(() -> {
      RestaurantEntity restaurant = new RestaurantEntity();
      restaurant.setName("Review Restaurant");
      restaurant.setAddress("1 Test Street");
      restaurant.setCity("Review City " + System.nanoTime());
      restaurant.setCuisine("Italian");
      restaurant.setLatitude(10);
      restaurant.setLongitude(20);
      entityManager.persist(restaurant);
      entityManager.flush();
      return restaurant.getId().intValue();
    });
  }

  private String validRestaurant(String name) {
    return """
        {"name":"%s","address":"1 Test Street","city":"Test City","cuisine":"Italian",
         "latitude":0,"longitude":0}
        """.formatted(name);
  }

  private long outboxCount() {
    return entityManager.createQuery("select count(e) from RestaurantSearchOutboxEntity e", Long.class)
        .getSingleResult();
  }
}
