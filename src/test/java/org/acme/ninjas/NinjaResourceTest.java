package org.acme.ninjas;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.acme.village.VillageEntity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@TestSecurity(user = "mission-admin", roles = "mission-admin")
class NinjaResourceTest {
  @Inject
  EntityManager entityManager;

  @Test
  void createsNinjaAndReturnsDtoWithVillageReference() {
    VillageEntity village = entityManager.find(VillageEntity.class, 1L);
    int populationBefore = village.getPopulation();

    String location = given()
        .contentType("application/json")
        .body("""
            {
              "name": "DTO Test Ninja",
              "age": 18,
              "level": "ADVANCED",
              "villageId": 1
            }
            """)
        .when()
        .post("/ninjas")
        .then()
        .statusCode(201)
        .header("Location", notNullValue())
        .body("name", equalTo("DTO Test Ninja"))
        .body("age", equalTo(18))
        .body("level", equalTo("ADVANCED"))
        .body("village.id", equalTo(1))
        .body("village.name", equalTo("Hidden Leaf Village"))
        .extract()
        .header("Location");

    given()
        .when()
        .get(location)
        .then()
        .statusCode(200)
        .body("name", equalTo("DTO Test Ninja"));

    given()
        .when()
        .get("/ninjas")
        .then()
        .statusCode(200)
        .body("items.find { it.name == 'DTO Test Ninja' }.id", notNullValue())
        .body("items.find { it.name == 'DTO Test Ninja' }.village.name", equalTo("Hidden Leaf Village"))
        .body("page", equalTo(0))
        .body("size", equalTo(20));

    entityManager.clear();
    VillageEntity updatedVillage = entityManager.find(VillageEntity.class, 1L);
    assertNotNull(updatedVillage);
    assertEquals(populationBefore + 1, updatedVillage.getPopulation());
  }

  @Test
  void rejectsInvalidCreateRequest() {
    given()
        .contentType("application/json")
        .body("""
            {
              "name": " ",
              "age": -1,
              "level": null
            }
            """)
        .when()
        .post("/ninjas")
        .then()
        .statusCode(400);
  }

  @Test
  void rejectsNinjaNameLongerThanDatabaseColumn() {
    given()
        .contentType("application/json")
        .body("""
            {
              "name": "%s",
              "age": 18,
              "level": "BEGINNER"
            }
            """.formatted("n".repeat(256)))
        .when()
        .post("/ninjas")
        .then()
        .statusCode(400);
  }

  @Test
  void returnsNotFoundWhenVillageDoesNotExist() {
    given()
        .contentType("application/json")
        .body("""
            {
              "name": "Orphan Ninja",
              "age": 18,
              "level": "BEGINNER",
              "villageId": 999999
            }
            """)
        .when()
        .post("/ninjas")
        .then()
        .statusCode(404);
  }

  @Test
  void returnsNotFoundWhenNinjaDoesNotExist() {
    given()
        .when()
        .get("/ninjas/999999")
        .then()
        .statusCode(404);
  }

  @Test
  void rejectsOutOfRangeNinjaListPagination() {
    given()
        .queryParam("size", 101)
        .when()
        .get("/ninjas")
        .then()
        .statusCode(400);
  }
}
