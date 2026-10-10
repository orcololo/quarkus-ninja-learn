package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class ApiAuthorizationTest {

  @Test
  @TestSecurity(user = "member", roles = "ninja")
  void ninjaCannotPerformAdministrativeWrites() {
    given()
        .contentType("application/json")
        .body("""
            {"name":"Unauthorized Ninja","age":20,"level":"BEGINNER"}
            """)
        .when()
        .post("/ninjas")
        .then()
        .statusCode(403);

    given()
        .contentType("application/json")
        .body("""
            {"name":"Unauthorized Village","location":"Nowhere","type":"SMALL"}
            """)
        .when()
        .post("/villages")
        .then()
        .statusCode(403);

    given()
        .contentType("application/json")
        .body("""
            {"name":"Updated Village","location":"Nowhere","type":"SMALL"}
            """)
        .when()
        .put("/villages/1")
        .then()
        .statusCode(403);

    given().when().delete("/villages/1").then().statusCode(403);
    given()
        .contentType("application/json")
        .body("{\"ninjaId\":1}")
        .when()
        .put("/villages/1/leader")
        .then()
        .statusCode(403);
    given()
        .when()
        .put("/villages/1/ninjas/1")
        .then()
        .statusCode(403);
    given()
        .when()
        .delete("/villages/1/ninjas/1")
        .then()
        .statusCode(403);
  }

  @Test
  void anonymousCannotCreateNinjasOrVillages() {
    given()
        .contentType("application/json")
        .body("""
            {"name":"Anonymous Ninja","age":20,"level":"BEGINNER"}
            """)
        .when()
        .post("/ninjas")
        .then()
        .statusCode(401);

    given()
        .contentType("application/json")
        .body("""
            {"name":"Anonymous Village","location":"Nowhere","type":"SMALL"}
            """)
        .when()
        .post("/villages")
        .then()
        .statusCode(401);
  }
}
