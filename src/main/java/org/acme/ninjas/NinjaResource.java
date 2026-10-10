package org.acme.ninjas;

import org.acme.dto.PageResponseDto;
import org.acme.ninjas.dto.CreateNinjaRequest;
import org.acme.ninjas.dto.NinjaResponseDto;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/ninjas")
public class NinjaResource {

  private final NinjaService ninjaService;

  NinjaResource(NinjaService ninjaService) {
    this.ninjaService = ninjaService;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public Response createNinja(@Valid CreateNinjaRequest request, @Context UriInfo uriInfo) {
    NinjaResponseDto ninja = ninjaService.createNinja(request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(ninja.id().toString()).build())
        .entity(ninja)
        .build();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public PageResponseDto<NinjaResponseDto> getAllNinjas(
      @QueryParam("page") @DefaultValue("0") @Min(0) @Max(1_000_000) int page,
      @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
    return ninjaService.getAllNinjas(page, size);
  }

  @GET
  @Path("/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public NinjaResponseDto getNinja(@PathParam("id") Long id) {
    return ninjaService.getNinja(id);
  }
}
