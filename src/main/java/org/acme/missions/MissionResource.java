package org.acme.missions;

import java.util.List;

import org.acme.missions.dto.CreateMissionRequest;
import org.acme.missions.dto.MissionResponseDto;
import org.acme.missions.dto.UpdateMissionRequest;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/missions")
@Produces(MediaType.APPLICATION_JSON)
public class MissionResource {
  private final MissionService missionService;

  MissionResource(MissionService missionService) {
    this.missionService = missionService;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public Response create(@Valid CreateMissionRequest request, @Context UriInfo uriInfo) {
    MissionResponseDto mission = missionService.create(request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(mission.id().toString()).build())
        .entity(mission)
        .build();
  }

  @GET
  public List<MissionResponseDto> list() {
    return missionService.list();
  }

  @GET
  @Path("/{id}")
  public MissionResponseDto get(@PathParam("id") Long id) {
    return missionService.get(id);
  }

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public MissionResponseDto update(@PathParam("id") Long id, @Valid UpdateMissionRequest request) {
    return missionService.update(id, request);
  }

  @POST
  @Path("/{id}/activate")
  @RolesAllowed("mission-admin")
  public MissionResponseDto activate(@PathParam("id") Long id) {
    return missionService.setActive(id, true);
  }

  @POST
  @Path("/{id}/deactivate")
  @RolesAllowed("mission-admin")
  public MissionResponseDto deactivate(@PathParam("id") Long id) {
    return missionService.setActive(id, false);
  }
}
