package org.acme.village;

import org.acme.dto.PageResponseDto;
import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.village.dto.AssignVillageLeaderRequest;
import org.acme.village.dto.CreateVillageRequest;
import org.acme.village.dto.UpdateVillageRequest;
import org.acme.village.dto.VillageResponseDto;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/villages")
public class VillageResource {

  private final VillageService villageService;

  VillageResource(VillageService villageService) {
    this.villageService = villageService;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public Response createVillage(@Valid CreateVillageRequest request, @Context UriInfo uriInfo) {
    VillageResponseDto village = villageService.createVillage(request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(village.id().toString()).build())
        .entity(village)
        .build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PageResponseDto<VillageResponseDto> getAllVillages(
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(1_000_000) int page,
      @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
    return villageService.getAllVillages(page, size);
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public VillageResponseDto getVillage(@PathParam("id") Long id) {
    return villageService.getVillage(id);
  }

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public VillageResponseDto updateVillage(
      @PathParam("id") Long id, @Valid UpdateVillageRequest request) {
    return villageService.updateVillage(id, request);
  }

  @DELETE
  @Path("/{id}")
  @RolesAllowed("mission-admin")
  public Response deleteVillage(@PathParam("id") Long id) {
    villageService.deleteVillage(id);
    return Response.noContent().build();
  }

  @PUT
  @Path("/{id}/leader")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public VillageResponseDto assignLeader(
      @PathParam("id") Long id, @Valid AssignVillageLeaderRequest request) {
    return villageService.assignLeader(id, request.ninjaId());
  }

  @DELETE
  @Path("/{id}/leader")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public VillageResponseDto removeLeader(@PathParam("id") Long id) {
    return villageService.removeLeader(id);
  }

  @GET
  @Path("/{id}/ninjas")
  @Produces(MediaType.APPLICATION_JSON)
  public PageResponseDto<NinjaResponseDto> getNinjas(
      @PathParam("id") Long id,
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(1_000_000) int page,
      @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
    return villageService.getNinjas(id, page, size);
  }

  @PUT
  @Path("/{id}/ninjas/{ninjaId}")
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public NinjaResponseDto assignNinja(@PathParam("id") Long id, @PathParam("ninjaId") Long ninjaId) {
    return villageService.assignNinja(id, ninjaId);
  }

  @DELETE
  @Path("/{id}/ninjas/{ninjaId}")
  @RolesAllowed("mission-admin")
  public Response removeNinja(@PathParam("id") Long id, @PathParam("ninjaId") Long ninjaId) {
    villageService.removeNinja(id, ninjaId);
    return Response.noContent().build();
  }
}
