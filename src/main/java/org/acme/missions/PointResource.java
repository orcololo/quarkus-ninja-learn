package org.acme.missions;

import org.acme.missions.dto.PointBalanceResponseDto;

import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/ninjas/{ninjaId}/points")
@Produces(MediaType.APPLICATION_JSON)
public class PointResource {
  private final NinjaMissionService ninjaMissionService;

  PointResource(NinjaMissionService ninjaMissionService) {
    this.ninjaMissionService = ninjaMissionService;
  }

  @GET
  @RolesAllowed({ "ninja", "mission-admin" })
  public PointBalanceResponseDto getPoints(
      @PathParam("ninjaId") Long ninjaId,
      @QueryParam("page") @DefaultValue("0") @Min(0) int page,
      @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
    return ninjaMissionService.getPointBalance(ninjaId, page, size);
  }
}
