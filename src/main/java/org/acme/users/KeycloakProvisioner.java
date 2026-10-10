package org.acme.users;

import java.net.URI;
import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

@ApplicationScoped
public class KeycloakProvisioner {
  private final KeycloakProvisioningClient client;

  @ConfigProperty(name = "keycloak.realm")
  String realm;

  @ConfigProperty(name = "keycloak.provisioner.client-id")
  String clientId;

  @ConfigProperty(name = "keycloak.provisioner.client-secret")
  String clientSecret;

  KeycloakProvisioner(@RestClient KeycloakProvisioningClient client) {
    this.client = client;
  }

  public String createUser(String username, String email, String password) {
    String token = accessToken();
    KeycloakProvisioningClient.KeycloakUser user = new KeycloakProvisioningClient.KeycloakUser(
        username,
        email,
        true,
        false,
        List.of(new KeycloakProvisioningClient.Credential("password", password, false)));

    String userId;
    try (Response response = client.createUser(realm, "Bearer " + token, user)) {
      if (response.getStatus() == Status.CONFLICT.getStatusCode()) {
        throw new WebApplicationException("Username or email is already registered", Status.CONFLICT);
      }
      if (response.getStatus() != Status.CREATED.getStatusCode()) {
        throw new WebApplicationException("Identity provider could not create the account",
            Status.BAD_GATEWAY);
      }
      URI location = response.getLocation();
      userId = location == null || location.getPath() == null
          ? null
          : location.getPath().substring(location.getPath().lastIndexOf('/') + 1);
      if (userId == null || userId.isBlank()) {
        WebApplicationException exception = new WebApplicationException(
            "Identity provider returned no account identifier", Status.BAD_GATEWAY);
        try {
          String createdUserId = findUserId(username, token);
          if (createdUserId != null) {
            deleteUser(createdUserId, token);
          } else {
            exception.addSuppressed(
                new IllegalStateException("Could not find incomplete Keycloak account for cleanup"));
          }
        } catch (RuntimeException cleanupFailure) {
          exception.addSuppressed(cleanupFailure);
        }
        throw exception;
      }
    }

    try {
      KeycloakProvisioningClient.KeycloakRole role = client.getRealmRole(realm, "Bearer " + token, "ninja");
      try (Response response = client.addRealmRoles(realm, userId, "Bearer " + token, List.of(role))) {
        if (response.getStatus() != Status.NO_CONTENT.getStatusCode()) {
          throw new WebApplicationException("Identity provider could not grant the Ninja role",
              Status.BAD_GATEWAY);
        }
      }
      return userId;
    } catch (RuntimeException exception) {
      try (Response ignored = client.deleteUser(realm, userId, "Bearer " + token)) {
        if (ignored.getStatus() != Status.NO_CONTENT.getStatusCode()
            && ignored.getStatus() != Status.NOT_FOUND.getStatusCode()) {
          exception.addSuppressed(new IllegalStateException("Could not remove incomplete Keycloak account"));
        }
      } catch (RuntimeException cleanupFailure) {
        exception.addSuppressed(cleanupFailure);
      }
      throw exception;
    }
  }

  public void deleteUser(String userId) {
    String token = accessToken();
    deleteUser(userId, token);
  }

  private void deleteUser(String userId, String token) {
    try (Response response = client.deleteUser(realm, userId, "Bearer " + token)) {
      if (response.getStatus() != Status.NO_CONTENT.getStatusCode()
          && response.getStatus() != Status.NOT_FOUND.getStatusCode()) {
        throw new WebApplicationException("Identity provider could not remove the incomplete account",
            Status.BAD_GATEWAY);
      }
    }
  }

  private String findUserId(String username, String token) {
    return client.findUsers(realm, "Bearer " + token, username, true).stream()
        .map(KeycloakProvisioningClient.KeycloakUserReference::id)
        .filter(id -> id != null && !id.isBlank())
        .findFirst()
        .orElse(null);
  }

  private String accessToken() {
    KeycloakProvisioningClient.TokenResponse token;
    try {
      token = client.token(realm, "client_credentials", clientId, clientSecret);
    } catch (RuntimeException exception) {
      throw new WebApplicationException("Identity provider provisioning is unavailable",
          exception, Status.BAD_GATEWAY);
    }
    if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
      throw new WebApplicationException("Identity provider provisioning is unavailable",
          Status.BAD_GATEWAY);
    }
    return token.accessToken();
  }
}
