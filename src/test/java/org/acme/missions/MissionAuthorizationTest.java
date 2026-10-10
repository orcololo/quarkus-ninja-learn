package org.acme.missions;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

@QuarkusTest
class MissionAuthorizationTest {

  @Test
  @TestSecurity(user = "123", roles = "ninja")
  void ninjaCannotCreateMissionOrApproveAssignments() {
    given()
        .contentType("application/json")
        .body("""
            {
              "title": "Unauthorized mission",
              "points": 10
            }
            """)
        .when()
        .post("/missions")
        .then()
        .statusCode(403);

    given()
        .when()
        .post("/ninja-missions/1/approve")
        .then()
        .statusCode(403);
  }

  @Test
  void anonymousCannotSubmitMission() {
    given()
        .when()
        .post("/ninja-missions/1/submit")
        .then()
        .statusCode(401);
  }
}
