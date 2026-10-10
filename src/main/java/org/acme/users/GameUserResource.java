package org.acme.users;

import java.util.List;

import org.acme.users.dto.CreateOwnedNinjaRequest;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.users.dto.GameUserPageResponseDto;
import org.acme.users.dto.GameUserResponseDto;
import org.acme.users.dto.DeliveryAddressRequest;
import org.acme.users.dto.DeliveryAddressResponseDto;
import org.acme.users.dto.RegisterUserRequest;
import org.acme.users.dto.UpdateAccountLevelRequest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class GameUserResource {
  private final GameUserService service;

  GameUserResource(GameUserService service) {
    this.service = service;
  }

  @POST
  @Path("/register")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response register(@Valid RegisterUserRequest request, @Context UriInfo uriInfo) {
    GameUserResponseDto user = service.register(request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(user.id().toString()).build())
        .entity(user)
        .build();
  }

  @GET
  @Path("/me")
  @RolesAllowed("ninja")
  public GameUserResponseDto me() {
    return service.getMe();
  }

  @GET
  @Path("/me/delivery-addresses")
  @RolesAllowed("ninja")
  public List<DeliveryAddressResponseDto> deliveryAddresses() {
    return service.listDeliveryAddresses();
  }

  @POST
  @Path("/me/delivery-addresses")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("ninja")
  public Response createDeliveryAddress(
      @Valid DeliveryAddressRequest request, @Context UriInfo uriInfo) {
    DeliveryAddressResponseDto address = service.createDeliveryAddress(request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(address.id().toString()).build())
        .entity(address)
        .build();
  }

  @PUT
  @Path("/me/delivery-addresses/{addressId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("ninja")
  public DeliveryAddressResponseDto updateDeliveryAddress(
      @PathParam("addressId") long addressId, @Valid DeliveryAddressRequest request) {
    return service.updateDeliveryAddress(addressId, request);
  }

  @PUT
  @Path("/me/delivery-addresses/{addressId}/default")
  @RolesAllowed("ninja")
  public DeliveryAddressResponseDto setDefaultDeliveryAddress(
      @PathParam("addressId") long addressId) {
    return service.setDefaultDeliveryAddress(addressId);
  }

  @DELETE
  @Path("/me/delivery-addresses/{addressId}")
  @RolesAllowed("ninja")
  public Response deleteDeliveryAddress(@PathParam("addressId") long addressId) {
    service.deleteDeliveryAddress(addressId);
    return Response.noContent().build();
  }

  @POST
  @Path("/me/ninjas")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("ninja")
  public Response createNinja(
      @Valid CreateOwnedNinjaRequest request, @Context UriInfo uriInfo) {
    NinjaResponseDto ninja = service.createNinja(request);
    return Response.created(uriInfo.getBaseUriBuilder()
        .path("ninjas")
        .path(ninja.id().toString())
        .build())
        .entity(ninja)
        .build();
  }

  @GET
  @RolesAllowed("mission-admin")
  public GameUserPageResponseDto list(
      @QueryParam("page") @DefaultValue("0") @Min(0) int page,
      @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
    return service.list(page, size);
  }

  @PUT
  @Path("/{userId}/level")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public GameUserResponseDto setLevel(
      @PathParam("userId") Long userId, @Valid UpdateAccountLevelRequest request) {
    return service.setLevel(userId, request);
  }

  @PUT
  @Path("/{userId}/ninjas/{ninjaId}")
  @RolesAllowed("mission-admin")
  public GameUserResponseDto linkNinja(
      @PathParam("userId") Long userId, @PathParam("ninjaId") Long ninjaId) {
    return service.linkNinja(userId, ninjaId);
  }

  @DELETE
  @Path("/{userId}/ninjas/{ninjaId}")
  @RolesAllowed("mission-admin")
  public GameUserResponseDto unlinkNinja(
      @PathParam("userId") Long userId, @PathParam("ninjaId") Long ninjaId) {
    return service.unlinkNinja(userId, ninjaId);
  }
}
