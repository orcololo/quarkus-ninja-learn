package org.acme.missions;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestSecurity(user = "987654321", roles = { "ninja", "mission-admin" })
class NinjaMissionWorkflowTest {
  private static final long NINJA_ID = 987654321L;

  @Inject
  EntityManager entityManager;

  @BeforeEach
  void ensureTestNinjaExists() {
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
        insert into GameUserEntity (id, subject, username, email, level)
        values (987654321, '987654321', 'mission-test-user', 'mission-test@example.invalid', 'BASIC')
        on conflict (id) do nothing
        """).executeUpdate());
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
        insert into GameUserEntity (id, subject, username, email, level)
        values (987654322, '987654322', 'other-mission-test-user', 'other-mission-test@example.invalid', 'BASIC')
        on conflict (id) do nothing
        """).executeUpdate());
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
        insert into NinjaEntity (id, age, level, name, village_id, owner_user_id)
        values (987654321, 25, 3, 'Mission Workflow Ninja', null, 987654321)
        on conflict (id) do update set owner_user_id = 987654321
        """).executeUpdate());
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
        insert into NinjaEntity (id, age, level, name, village_id, owner_user_id)
        values (987654322, 25, 3, 'Different Ninja', null, 987654322)
        on conflict (id) do update set owner_user_id = 987654322
        """).executeUpdate());
  }

  @Test
  void approvalAwardsPointsOnlyOnceAndReturnsBalance() {
    int previousBalance = currentBalance();
    long missionId = createMission("Escort a client", 40);
    long assignmentId = assignMission(missionId);

    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/submit")
        .then()
        .statusCode(200)
        .body("status", equalTo("SUBMITTED"));

    given()
        .when()
        .get("/ninja-missions/submitted")
        .then()
        .statusCode(200)
        .body("find { it.id == %d }.status".formatted(assignmentId), equalTo("SUBMITTED"));

    long transactionId = ((Number) given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/approve")
        .then()
        .statusCode(200)
        .body("points", equalTo(40))
        .extract()
        .path("id")).longValue();

    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/approve")
        .then()
        .statusCode(200)
        .body("id", equalTo((int) transactionId));

    given()
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(200)
        .body("balance", equalTo(previousBalance + 40))
        .body("transactions.find { it.ninjaMissionId == %d }.points".formatted(assignmentId), equalTo(40));

    given()
        .when()
        .get("/ninja-missions/" + assignmentId)
        .then()
        .statusCode(200)
        .body("status", equalTo("APPROVED"));
  }

  @Test
  void rejectsRepeatedAssignmentAndAllowsRejectWithoutPoints() {
    int previousBalance = currentBalance();
    long missionId = createMission("Deliver a message", 15);
    long assignmentId = assignMission(missionId);

    given()
        .when()
        .post("/ninjas/" + NINJA_ID + "/missions/" + missionId)
        .then()
        .statusCode(409);

    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/submit")
        .then()
        .statusCode(200)
        .body("status", equalTo("SUBMITTED"));

    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/reject")
        .then()
        .statusCode(200)
        .body("status", equalTo("REJECTED"));

    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/approve")
        .then()
        .statusCode(409);

    given()
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(200)
        .body("balance", equalTo(previousBalance));
  }

  @Test
  void rejectsInactiveMissionAssignmentAndInvalidStateTransitions() {
    long missionId = createMission("Inactive mission", 5);
    given()
        .when()
        .post("/missions/" + missionId + "/deactivate")
        .then()
        .statusCode(200)
        .body("active", equalTo(false));

    given()
        .when()
        .post("/ninjas/" + NINJA_ID + "/missions/" + missionId)
        .then()
        .statusCode(409);

    given()
        .when()
        .post("/missions/" + missionId + "/activate")
        .then()
        .statusCode(200)
        .body("active", equalTo(true));

    long assignmentId = assignMission(missionId);
    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/approve")
        .then()
        .statusCode(409);
  }

  @Test
  void ninjaCannotSubmitAnotherNinjasAssignment() {
    long missionId = createMission("Protect another village", 10);
    long assignmentId = ((Number) given()
        .when()
        .post("/ninjas/987654322/missions/" + missionId)
        .then()
        .statusCode(201)
        .extract()
        .path("id")).longValue();

    given()
        .when()
        .post("/ninja-missions/" + assignmentId + "/submit")
        .then()
        .statusCode(403);
  }

  @Test
  void pointHistoryIsPaginatedWhileBalanceUsesAllTransactions() {
    int balanceBefore = currentBalance();
    int countBefore = ((Number) given()
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(200)
        .extract()
        .path("totalTransactions")).intValue();

    completeMission("First paginated mission", 7);
    completeMission("Second paginated mission", 13);

    given()
        .queryParam("page", 0)
        .queryParam("size", 1)
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(200)
        .body("balance", equalTo(balanceBefore + 20))
        .body("page", equalTo(0))
        .body("size", equalTo(1))
        .body("totalTransactions", equalTo(countBefore + 2))
        .body("transactions", org.hamcrest.Matchers.hasSize(1));

    given()
        .queryParam("page", 1)
        .queryParam("size", 1)
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(200)
        .body("balance", equalTo(balanceBefore + 20))
        .body("totalTransactions", equalTo(countBefore + 2))
        .body("transactions", org.hamcrest.Matchers.hasSize(1));
  }

  @Test
  void rejectsInvalidPointHistoryPagination() {
    given()
        .queryParam("page", -1)
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(400);

    given()
        .queryParam("size", 101)
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(400);
  }

  private long createMission(String title, int points) {
    return ((Number) given()
        .contentType("application/json")
        .body("""
            {
              "title": "%s",
              "description": "Mission workflow test",
              "points": %d
            }
            """.formatted(title, points))
        .when()
        .post("/missions")
        .then()
        .statusCode(201)
        .body("id", notNullValue())
        .extract()
        .path("id")).longValue();
  }

  private long assignMission(long missionId) {
    return ((Number) given()
        .when()
        .post("/ninjas/" + NINJA_ID + "/missions/" + missionId)
        .then()
        .statusCode(201)
        .body("status", equalTo("ASSIGNED"))
        .extract()
        .path("id")).longValue();
  }

  private int currentBalance() {
    return given()
        .when()
        .get("/ninjas/" + NINJA_ID + "/points")
        .then()
        .statusCode(200)
        .extract()
        .path("balance");
  }

  private void completeMission(String title, int points) {
    long missionId = createMission(title, points);
    long assignmentId = assignMission(missionId);
    given().when().post("/ninja-missions/" + assignmentId + "/submit").then().statusCode(200);
    given().when().post("/ninja-missions/" + assignmentId + "/approve").then().statusCode(200);
  }
}
