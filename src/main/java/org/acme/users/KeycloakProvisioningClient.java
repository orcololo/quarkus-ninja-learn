package org.acme.users;

import java.util.List;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;

@Path("/")
@RegisterRestClient(configKey = "keycloak-admin")
public interface KeycloakProvisioningClient {
  @POST
  @Path("/realms/{realm}/protocol/openid-connect/token")
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  TokenResponse token(
      @PathParam("realm") String realm,
      @RestForm("grant_type") String grantType,
      @RestForm("client_id") String clientId,
      @RestForm("client_secret") String clientSecret);

  @POST
  @Path("/admin/realms/{realm}/users")
  @Consumes(MediaType.APPLICATION_JSON)
  Response createUser(
      @PathParam("realm") String realm,
      @HeaderParam("Authorization") String authorization,
      KeycloakUser user);

  @GET
  @Path("/admin/realms/{realm}/roles/{roleName}")
  @Produces(MediaType.APPLICATION_JSON)
  KeycloakRole getRealmRole(
      @PathParam("realm") String realm,
      @HeaderParam("Authorization") String authorization,
      @PathParam("roleName") String roleName);

  @GET
  @Path("/admin/realms/{realm}/users")
  @Produces(MediaType.APPLICATION_JSON)
  List<KeycloakUserReference> findUsers(
      @PathParam("realm") String realm,
      @HeaderParam("Authorization") String authorization,
      @QueryParam("username") String username,
      @QueryParam("exact") boolean exact);

  @POST
  @Path("/admin/realms/{realm}/users/{userId}/role-mappings/realm")
  @Consumes(MediaType.APPLICATION_JSON)
  Response addRealmRoles(
      @PathParam("realm") String realm,
      @PathParam("userId") String userId,
      @HeaderParam("Authorization") String authorization,
      List<KeycloakRole> roles);

  @DELETE
  @Path("/admin/realms/{realm}/users/{userId}")
  Response deleteUser(
      @PathParam("realm") String realm,
      @PathParam("userId") String userId,
      @HeaderParam("Authorization") String authorization);

  record TokenResponse(@JsonProperty("access_token") String accessToken) {
  }

  record KeycloakRole(String id, String name) {
  }

  record KeycloakUserReference(String id) {
  }

  record KeycloakUser(
      String username,
      String email,
      boolean enabled,
      boolean emailVerified,
      List<Credential> credentials) {
  }

  record Credential(String type, String value, boolean temporary) {
  }
}
