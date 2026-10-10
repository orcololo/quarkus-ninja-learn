package org.acme.users;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
class GameUserResourceTest {
  private static final long USER_ID = 765432101L;

  @Inject
  EntityManager entityManager;

  @BeforeEach
  void createTestAccount() {
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
        update NinjaEntity set owner_user_id = null where owner_user_id = 765432101
        """).executeUpdate());
    QuarkusTransaction.requiringNew().run(() -> entityManager.createNativeQuery("""
        insert into GameUserEntity (id, subject, username, email, level)
        values (765432101, 'account-test-subject', 'account-test-user',
                'account-test@example.invalid', 'BASIC')
        on conflict (id) do update set level = 'BASIC'
        """).executeUpdate());
  }

  @Test
  @TestSecurity(user = "account-test-subject", roles = "ninja")
  void basicAccountCanCreateOnlyOneOwnedNinja() {
    given()
        .when()
        .get("/users/me")
        .then()
        .statusCode(200)
        .body("username", equalTo("account-test-user"))
        .body("level", equalTo("BASIC"))
        .body("ninjaLimit", equalTo(1));

    given()
        .contentType("application/json")
        .body("""
            {"name":"My Ninja","age":12,"level":"BEGINNER"}
            """)
        .when()
        .post("/users/me/ninjas")
        .then()
        .statusCode(201);

    given()
        .contentType("application/json")
        .body("""
            {"name":"Second Ninja","age":13,"level":"BEGINNER"}
            """)
        .when()
        .post("/users/me/ninjas")
        .then()
        .statusCode(409);
  }

  @Test
  @TestSecurity(user = "account-test-subject", roles = "ninja")
  void ninjaCannotChangeAccountTierOrLinkOtherNinjas() {
    given()
        .contentType("application/json")
        .body("{\"level\":\"ADMIN\"}")
        .when()
        .put("/users/{id}/level", USER_ID)
        .then()
        .statusCode(403);

    given()
        .when()
        .put("/users/{userId}/ninjas/{ninjaId}", USER_ID, 1)
        .then()
        .statusCode(403);
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void adminCanPromoteAccountsButCannotLowerBelowOwnedNinjaCount() {
    given()
        .contentType("application/json")
        .body("{\"level\":\"PREMIUM\"}")
        .when()
        .put("/users/{id}/level", USER_ID)
        .then()
        .statusCode(200)
        .body("level", equalTo("PREMIUM"))
        .body("ninjaLimit", equalTo(2));

    given()
        .contentType("application/json")
        .body("{\"level\":\"ADMIN\"}")
        .when()
        .put("/users/{id}/level", USER_ID)
        .then()
        .statusCode(200)
        .body("level", equalTo("ADMIN"))
        .body("ninjaLimit", org.hamcrest.Matchers.nullValue());

    given()
        .when()
        .get("/users?page=0&size=100")
        .then()
        .statusCode(200)
        .body("items.find { it.id == 765432101 }.level", equalTo("ADMIN"))
        .body("items.find { it.id == 765432101 }.ninjaLimit", org.hamcrest.Matchers.nullValue())
        .body("page", equalTo(0))
        .body("size", equalTo(100));
  }

  @Test
  @TestSecurity(user = "account-test-subject", roles = "ninja")
  void adminAccountHasNullLimitInItsOwnAccountResponse() {
    QuarkusTransaction.requiringNew().run(() ->
        entityManager.createNativeQuery("""
            update GameUserEntity set level = 'ADMIN' where id = 765432101
            """).executeUpdate());

    given()
        .when()
        .get("/users/me")
        .then()
        .statusCode(200)
        .body("level", equalTo("ADMIN"))
        .body("ninjaLimit", org.hamcrest.Matchers.nullValue());
  }

  @Test
  @TestSecurity(user = "mission-admin", roles = "mission-admin")
  void accountListValidatesPaginationBounds() {
    given()
        .when()
        .get("/users?page=0&size=101")
        .then()
        .statusCode(400);
  }
}
