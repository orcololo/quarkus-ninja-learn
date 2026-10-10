package org.acme.missions;

import org.acme.missions.dto.NinjaMissionResponseDto;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/ninjas/{ninjaId}/missions")
@Produces(MediaType.APPLICATION_JSON)
public class NinjaMissionAssignmentResource {
  private final NinjaMissionService ninjaMissionService;

  NinjaMissionAssignmentResource(NinjaMissionService ninjaMissionService) {
    this.ninjaMissionService = ninjaMissionService;
  }

  @POST
  @Path("/{missionId}")
  @RolesAllowed("mission-admin")
  public Response assign(
      @PathParam("ninjaId") Long ninjaId,
      @PathParam("missionId") Long missionId,
      @Context UriInfo uriInfo) {
    NinjaMissionResponseDto assignment = ninjaMissionService.assign(ninjaId, missionId);
    return Response.created(uriInfo.getBaseUriBuilder()
        .path("ninja-missions")
        .path(assignment.id().toString())
        .build())
        .entity(assignment)
        .build();
  }
}
