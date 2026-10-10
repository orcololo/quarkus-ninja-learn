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
        delete from user_delivery_address where user_id = 765432101
        """).executeUpdate());
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

  @Test
  @TestSecurity(user = "account-test-subject", roles = "ninja")
  void accountCanManageAddressesAndDefaultMovesToOldestWhenDeleted() {
    int firstId = given()
        .contentType("application/json")
        .body(validAddress("Home"))
        .when()
        .post("/users/me/delivery-addresses")
        .then()
        .statusCode(201)
        .body("isDefault", equalTo(true))
        .extract()
        .path("id");

    int secondId = given()
        .contentType("application/json")
        .body(validAddress("Work"))
        .when()
        .post("/users/me/delivery-addresses")
        .then()
        .statusCode(201)
        .body("isDefault", equalTo(false))
        .extract()
        .path("id");

    given()
        .when()
        .put("/users/me/delivery-addresses/{id}/default", secondId)
        .then()
        .statusCode(200)
        .body("isDefault", equalTo(true));

    given()
        .contentType("application/json")
        .body(validAddress("Office"))
        .when()
        .put("/users/me/delivery-addresses/{id}", secondId)
        .then()
        .statusCode(200)
        .body("label", equalTo("Office"))
        .body("phone", equalTo("+5511999999999"))
        .body("isDefault", equalTo(true));

    given()
        .when()
        .get("/users/me/delivery-addresses")
        .then()
        .statusCode(200)
        .body("size()", equalTo(2))
        .body("[0].id", equalTo(secondId))
        .body("[0].country", equalTo("BR"));

    given()
        .when()
        .delete("/users/me/delivery-addresses/{id}", secondId)
        .then()
        .statusCode(204);
    given()
        .when()
        .get("/users/me/delivery-addresses")
        .then()
        .statusCode(200)
        .body("size()", equalTo(1))
        .body("[0].id", equalTo(firstId))
        .body("[0].isDefault", equalTo(true));

    given()
        .contentType("application/json")
        .body("""
            {"label":"Invalid","recipientName":"Name","phone":"bad","street":"Rua A",
             "number":"1","neighborhood":"Centro","city":"Sao Paulo","state":"SP",
             "postalCode":"01000-000","country":"BR"}
            """)
        .when()
        .post("/users/me/delivery-addresses")
        .then()
        .statusCode(400);
  }

  @Test
  @TestSecurity(user = "other-account-subject", roles = "ninja")
  void accountCannotReadOrModifyAnotherUsersDeliveryAddress() {
    int addressId = QuarkusTransaction.requiringNew().call(() -> {
      GameUserEntity owner = entityManager.find(GameUserEntity.class, USER_ID);
      UserDeliveryAddressEntity address = new UserDeliveryAddressEntity();
      address.setUser(owner);
      address.setCreatedAt(java.time.Instant.now());
      address.setLabel("Private");
      address.setRecipientName("Account User");
      address.setPhone("+5511999999999");
      address.setStreet("Rua A");
      address.setNumber("1");
      address.setNeighborhood("Centro");
      address.setCity("Sao Paulo");
      address.setState("SP");
      address.setPostalCode("01000-000");
      address.setCountry("BR");
      address.setDefaultAddress(true);
      entityManager.persist(address);
      entityManager.flush();
      GameUserEntity otherUser = new GameUserEntity();
      otherUser.setSubject("other-account-subject");
      otherUser.setUsername("other-account");
      otherUser.setEmail("other-account@example.invalid");
      otherUser.setLevel(AccountLevel.BASIC);
      entityManager.persist(otherUser);
      entityManager.flush();
      return address.getId().intValue();
    });

    given()
        .when()
        .get("/users/me/delivery-addresses")
        .then()
        .statusCode(200)
        .body("size()", equalTo(0));
    given()
        .when()
        .delete("/users/me/delivery-addresses/{id}", addressId)
        .then()
        .statusCode(404);
    given()
        .contentType("application/json")
        .body(validAddress("Attempted update"))
        .when()
        .put("/users/me/delivery-addresses/{id}", addressId)
        .then()
        .statusCode(404);
  }

  @Test
  void deliveryAddressesRequireAuthentication() {
    given()
        .when()
        .get("/users/me/delivery-addresses")
        .then()
        .statusCode(401);
  }

  private String validAddress(String label) {
    return """
        {
          "label":"%s",
          "recipientName":"Account User",
          "phone":"+5511999999999",
          "street":"Rua das Flores",
          "number":"123",
          "complement":"Apto 42",
          "neighborhood":"Centro",
          "city":"Sao Paulo",
          "state":"SP",
          "postalCode":"01000-000",
          "country":"br",
          "deliveryInstructions":"Interfone 42"
        }
        """.formatted(label);
  }
}
