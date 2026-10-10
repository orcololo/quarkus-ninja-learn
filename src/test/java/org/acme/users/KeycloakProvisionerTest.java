package org.acme.users;

import java.util.List;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KeycloakProvisionerTest {
  @Test
  void removesCreatedAccountWhenKeycloakOmitsLocationHeader() {
    MissingLocationClient client = new MissingLocationClient();
    KeycloakProvisioner provisioner = new KeycloakProvisioner(client);
    provisioner.realm = "ninja";
    provisioner.clientId = "provisioner";
    provisioner.clientSecret = "secret";

    WebApplicationException exception = assertThrows(
        WebApplicationException.class,
        () -> provisioner.createUser("new-player", "new-player@example.com", "strong-password"));

    assertEquals(Response.Status.BAD_GATEWAY.getStatusCode(), exception.getResponse().getStatus());
    assertEquals("created-user-id", client.deletedUserId);
  }

  private static class MissingLocationClient implements KeycloakProvisioningClient {
    private String deletedUserId;

    @Override
    public TokenResponse token(String realm, String grantType, String clientId, String clientSecret) {
      return new TokenResponse("access-token");
    }

    @Override
    public Response createUser(String realm, String authorization, KeycloakUser user) {
      return Response.status(Response.Status.CREATED).build();
    }

    @Override
    public KeycloakRole getRealmRole(String realm, String authorization, String roleName) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<KeycloakUserReference> findUsers(
        String realm, String authorization, String username, boolean exact) {
      assertEquals("new-player", username);
      return List.of(new KeycloakUserReference("created-user-id"));
    }

    @Override
    public Response addRealmRoles(
        String realm, String userId, String authorization, List<KeycloakRole> roles) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Response deleteUser(String realm, String userId, String authorization) {
      deletedUserId = userId;
      return Response.noContent().build();
    }
  }
}
