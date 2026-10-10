package org.acme.missions;

import java.util.List;

import org.acme.missions.dto.NinjaMissionResponseDto;
import org.acme.missions.dto.PointTransactionResponseDto;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/ninja-missions")
@Produces(MediaType.APPLICATION_JSON)
public class NinjaMissionResource {
  private final NinjaMissionService ninjaMissionService;

  NinjaMissionResource(NinjaMissionService ninjaMissionService) {
    this.ninjaMissionService = ninjaMissionService;
  }

  @GET
  @Path("/submitted")
  @RolesAllowed("mission-admin")
  public List<NinjaMissionResponseDto> listSubmitted() {
    return ninjaMissionService.listSubmitted();
  }

  @GET
  @Path("/{id}")
  @RolesAllowed({ "ninja", "mission-admin" })
  public NinjaMissionResponseDto get(@PathParam("id") Long id) {
    return ninjaMissionService.get(id);
  }

  @POST
  @Path("/{id}/submit")
  @RolesAllowed("ninja")
  public NinjaMissionResponseDto submit(@PathParam("id") Long id) {
    return ninjaMissionService.submit(id);
  }

  @POST
  @Path("/{id}/approve")
  @RolesAllowed("mission-admin")
  public PointTransactionResponseDto approve(@PathParam("id") Long id) {
    return ninjaMissionService.approve(id);
  }

  @POST
  @Path("/{id}/reject")
  @RolesAllowed("mission-admin")
  public NinjaMissionResponseDto reject(@PathParam("id") Long id) {
    return ninjaMissionService.reject(id);
  }
}
