package org.acme.village;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.acme.ninjas.NinjaLevelEnum;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestSecurity(user = "mission-admin", roles = "mission-admin")
class VillageResourceTest {
  @Test
  void rejectsOutOfRangeVillagePagination() {
    given()
        .queryParam("page", -1)
        .when()
        .get("/villages")
        .then()
        .statusCode(400);
  }

  @Test
  void createsListsAndGetsVillage() {
    String location = given()
        .contentType("application/json")
        .body("""
            {
              "name": "Test Village",
              "location": "Land of Test",
              "description": "Created by a resource test",
              "type": "SMALL"
            }
            """)
        .when()
        .post("/villages")
        .then()
        .statusCode(201)
        .header("Location", notNullValue())
        .body("name", equalTo("Test Village"))
        .body("population", equalTo(0))
        .body("type", equalTo("SMALL"))
        .extract()
        .header("Location");

    given()
        .when()
        .get(location)
        .then()
        .statusCode(200)
        .body("name", equalTo("Test Village"));

    given()
        .when()
        .get("/villages")
        .then()
        .statusCode(200)
        .body("items.find { it.name == 'Test Village' }.id", notNullValue())
        .body("page", equalTo(0))
        .body("size", equalTo(20))
        .body("total", org.hamcrest.Matchers.greaterThan(0));
  }

  @Test
  void assigningLeaderCountsThemAsVillagePopulation() {
    int leaderId = given()
        .contentType("application/json")
        .body("""
            {
              "name": "Village Leader",
              "age": 30,
              "level": "%s"
            }
            """.formatted(NinjaLevelEnum.MASTER))
        .when()
        .post("/ninjas")
        .then()
        .statusCode(201)
        .extract()
        .path("id");

    given()
        .contentType("application/json")
        .body("""
            {
              "name": "Leader Village",
              "location": "Land of Test",
              "type": "SMALL",
              "leaderId": %d
            }
            """.formatted(leaderId))
        .when()
        .post("/villages")
        .then()
        .statusCode(201)
        .body("population", equalTo(1))
        .body("leader.id", equalTo(leaderId))
        .body("leader.name", equalTo("Village Leader"));
  }

  @Test
  void returnsNotFoundForMissingVillage() {
    given()
        .when()
        .get("/villages/999999")
        .then()
        .statusCode(404);
  }

  @Test
  void rejectsInvalidVillageRequest() {
    given()
        .contentType("application/json")
        .body("""
            {
              "name": " ",
              "location": "",
              "type": null
            }
            """)
        .when()
        .post("/villages")
        .then()
        .statusCode(400);
  }

  @Test
  void rejectsVillageFieldsLongerThanDatabaseColumns() {
    String tooLong = "x".repeat(256);
    for (String body : new String[] {
        """
            {"name":"%s","location":"Land","type":"SMALL"}
            """.formatted(tooLong),
        """
            {"name":"Village","location":"%s","type":"SMALL"}
            """.formatted(tooLong),
        """
            {"name":"Village","location":"Land","description":"%s","type":"SMALL"}
            """.formatted(tooLong)
    }) {
      given()
          .contentType("application/json")
          .body(body)
          .when()
          .post("/villages")
          .then()
          .statusCode(400);
    }

    int villageId = createVillage("Village for validation");
    for (String body : new String[] {
        """
            {"name":"%s","location":"Land","type":"SMALL"}
            """.formatted(tooLong),
        """
            {"name":"Village","location":"%s","type":"SMALL"}
            """.formatted(tooLong),
        """
            {"name":"Village","location":"Land","description":"%s","type":"SMALL"}
            """.formatted(tooLong)
    }) {
      given()
          .contentType("application/json")
          .body(body)
          .when()
          .put("/villages/{id}", villageId)
          .then()
          .statusCode(400);
    }
  }

  @Test
  void returnsNotFoundForUnknownLeader() {
    given()
        .contentType("application/json")
        .body("""
            {
              "name": "Leaderless Test Village",
              "location": "Land of Test",
              "type": "SMALL",
              "leaderId": 999999
            }
            """)
        .when()
        .post("/villages")
        .then()
        .statusCode(404);
  }

  @Test
  void assignsAndRemovesLeaderWithoutRemovingVillageMembership() {
    int ninjaId = createNinja("Appointed Leader");
    int villageId = createVillage("Leader Assignment Village");

    given()
        .contentType("application/json")
        .body("{\"ninjaId\":%d}".formatted(ninjaId))
        .when()
        .put("/villages/{id}/leader", villageId)
        .then()
        .statusCode(200)
        .body("leader.id", equalTo(ninjaId))
        .body("population", equalTo(1));

    given()
        .contentType("application/json")
        .body("{\"ninjaId\":%d}".formatted(ninjaId))
        .when()
        .put("/villages/{id}/leader", villageId)
        .then()
        .statusCode(200)
        .body("population", equalTo(1));

    given()
        .when()
        .delete("/villages/{id}/leader", villageId)
        .then()
        .statusCode(200)
        .body("leader", org.hamcrest.Matchers.nullValue())
        .body("population", equalTo(1));

    given()
        .when()
        .get("/villages/{id}/ninjas", villageId)
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", equalTo(ninjaId))
        .body("total", equalTo(1));
  }

  @Test
  void assignsMovesListsAndRemovesNinjasWithCorrectPopulation() {
    int ninjaId = createNinja("Traveling Ninja");
    int firstVillageId = createVillage("First Ninja Village");
    int secondVillageId = createVillage("Second Ninja Village");

    given()
        .when()
        .put("/villages/{id}/ninjas/{ninjaId}", firstVillageId, ninjaId)
        .then()
        .statusCode(200)
        .body("village.id", equalTo(firstVillageId));

    given()
        .when()
        .get("/villages/{id}/ninjas", firstVillageId)
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("total", equalTo(1));

    given()
        .queryParam("size", 101)
        .when()
        .get("/villages/{id}/ninjas", firstVillageId)
        .then()
        .statusCode(400);

    given()
        .when()
        .put("/villages/{id}/ninjas/{ninjaId}", secondVillageId, ninjaId)
        .then()
        .statusCode(200)
        .body("village.id", equalTo(secondVillageId));

    given()
        .when()
        .get("/villages/{id}", firstVillageId)
        .then()
        .statusCode(200)
        .body("population", equalTo(0));

    given()
        .when()
        .delete("/villages/{id}/ninjas/{ninjaId}", secondVillageId, ninjaId)
        .then()
        .statusCode(204);

    given()
        .when()
        .get("/villages/{id}", secondVillageId)
        .then()
        .statusCode(200)
        .body("population", equalTo(0));
  }

  @Test
  void updatesAndDeletesVillageWithoutDeletingItsNinjas() {
    int ninjaId = createNinja("Village-less Ninja");
    int villageId = createVillage("Village to Update");

    given()
        .when()
        .put("/villages/{id}/ninjas/{ninjaId}", villageId, ninjaId)
        .then()
        .statusCode(200);

    given()
        .contentType("application/json")
        .body("""
            {
              "name": "Updated Village",
              "location": "New Location",
              "description": "Updated details",
              "type": "CAPITAL"
            }
            """)
        .when()
        .put("/villages/{id}", villageId)
        .then()
        .statusCode(200)
        .body("name", equalTo("Updated Village"))
        .body("population", equalTo(1));

    given()
        .when()
        .delete("/villages/{id}", villageId)
        .then()
        .statusCode(204);

    given()
        .when()
        .get("/ninjas/{id}", ninjaId)
        .then()
        .statusCode(200)
        .body("village", org.hamcrest.Matchers.nullValue());

    given()
        .when()
        .get("/villages/{id}", villageId)
        .then()
        .statusCode(404);
  }

  private int createNinja(String name) {
    return given()
        .contentType("application/json")
        .body("""
            {
              "name": "%s",
              "age": 20,
              "level": "BEGINNER"
            }
            """.formatted(name))
        .when()
        .post("/ninjas")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }

  private int createVillage(String name) {
    return given()
        .contentType("application/json")
        .body("""
            {
              "name": "%s",
              "location": "Land of Test",
              "type": "SMALL"
            }
            """.formatted(name))
        .when()
        .post("/villages")
        .then()
        .statusCode(201)
        .extract()
        .path("id");
  }
}
