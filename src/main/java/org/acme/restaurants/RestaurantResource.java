package org.acme.restaurants;

import org.acme.restaurants.dto.CreateRestaurantRequest;
import org.acme.restaurants.dto.CreateRestaurantReviewRequest;
import org.acme.restaurants.dto.RestaurantResponseDto;
import org.acme.restaurants.dto.RestaurantReviewResponseDto;
import org.acme.restaurants.dto.RestaurantReviewPageResponseDto;
import org.acme.restaurants.dto.RestaurantSearchPageResponseDto;
import org.acme.restaurants.dto.UpdateRestaurantRequest;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
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

@Path("/restaurants")
@Produces(MediaType.APPLICATION_JSON)
public class RestaurantResource {
  private final RestaurantService service;

  RestaurantResource(RestaurantService service) {
    this.service = service;
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public Response create(@Valid CreateRestaurantRequest request, @Context UriInfo uriInfo) {
    RestaurantResponseDto restaurant = service.create(request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(restaurant.id().toString()).build())
        .entity(restaurant)
        .build();
  }

  @GET
  @Path("/{id}")
  public RestaurantResponseDto get(@PathParam("id") long id) {
    return service.get(id);
  }

  @PUT
  @Path("/{id}")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public RestaurantResponseDto update(
      @PathParam("id") long id, @Valid UpdateRestaurantRequest request) {
    return service.update(id, request);
  }

  @DELETE
  @Path("/{id}")
  @RolesAllowed("mission-admin")
  public Response delete(@PathParam("id") long id) {
    service.delete(id);
    return Response.noContent().build();
  }

  @GET
  @Path("/search")
  public RestaurantSearchPageResponseDto search(
      @QueryParam("q") @Size(max = 100) String query,
      @QueryParam("city") @Size(max = 120) String city,
      @QueryParam("cuisine") @Size(max = 100) String cuisine,
      @QueryParam("latitude") @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
      @QueryParam("longitude") @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
      @QueryParam("radiusKm") @DecimalMin("0.1") @DecimalMax("1000.0") Double radiusKm,
      @QueryParam("page") @Min(0) @Max(99) @jakarta.ws.rs.DefaultValue("0") int page,
      @QueryParam("size") @Min(1) @Max(100) @jakarta.ws.rs.DefaultValue("20") int size) {
    return service.search(query, city, cuisine, latitude, longitude, radiusKm, page, size);
  }

  @GET
  @Path("/{id}/reviews")
  public RestaurantReviewPageResponseDto reviews(
      @PathParam("id") long restaurantId,
      @QueryParam("page") @Min(0) @Max(1_000_000) @jakarta.ws.rs.DefaultValue("0") int page,
      @QueryParam("size") @Min(1) @Max(100) @jakarta.ws.rs.DefaultValue("20") int size) {
    return service.listReviews(restaurantId, page, size);
  }

  @GET
  @Path("/{id}/reviews/{reviewId}")
  public RestaurantReviewResponseDto review(
      @PathParam("id") long restaurantId, @PathParam("reviewId") long reviewId) {
    return service.getReview(restaurantId, reviewId);
  }

  @POST
  @Path("/{id}/reviews")
  @Consumes(MediaType.APPLICATION_JSON)
  @Authenticated
  public Response createReview(
      @PathParam("id") long restaurantId, @Valid CreateRestaurantReviewRequest request,
      @Context UriInfo uriInfo) {
    RestaurantReviewResponseDto review = service.createReview(restaurantId, request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(review.id().toString()).build())
        .entity(review)
        .build();
  }

  @POST
  @Path("/reindex")
  @RolesAllowed("mission-admin")
  public Response reindex() {
    int queued = service.queueReindex();
    return Response.accepted().entity(new ReindexResponse(queued)).build();
  }

  public record ReindexResponse(int restaurantsQueued) {
  }
}
