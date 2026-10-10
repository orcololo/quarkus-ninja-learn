package org.acme.restaurants;

import org.acme.restaurants.dto.CreateRestaurantRequest;
import org.acme.restaurants.dto.CreateRestaurantReviewRequest;
import org.acme.restaurants.dto.MenuItemRequest;
import org.acme.restaurants.dto.MenuItemResponseDto;
import org.acme.restaurants.dto.MenuItemSearchPageResponseDto;
import org.acme.restaurants.dto.RestaurantResponseDto;
import org.acme.restaurants.dto.RestaurantReviewResponseDto;
import org.acme.restaurants.dto.RestaurantReviewPageResponseDto;
import org.acme.restaurants.dto.RestaurantReindexResponseDto;
import org.acme.restaurants.dto.RestaurantSearchPageResponseDto;
import org.acme.restaurants.dto.UpdateRestaurantRequest;
import org.acme.dto.PageResponseDto;

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
import jakarta.ws.rs.DefaultValue;
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
  @Authenticated
  public Response delete(@PathParam("id") long id) {
    service.delete(id);
    return Response.noContent().build();
  }

  @GET
  @Path("/{id}/menu-items")
  public PageResponseDto<MenuItemResponseDto> menuItems(
      @PathParam("id") long restaurantId,
      @QueryParam("page") @Min(0) @Max(1_000_000) @DefaultValue("0") int page,
      @QueryParam("size") @Min(1) @Max(100) @DefaultValue("20") int size) {
    return service.listMenuItems(restaurantId, page, size);
  }

  @POST
  @Path("/{id}/menu-items")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public Response createMenuItem(
      @PathParam("id") long restaurantId, @Valid MenuItemRequest request,
      @Context UriInfo uriInfo) {
    MenuItemResponseDto item = service.createMenuItem(restaurantId, request);
    return Response.created(uriInfo.getAbsolutePathBuilder().path(item.id().toString()).build())
        .entity(item)
        .build();
  }

  @GET
  @Path("/{id}/menu-items/{itemId}")
  public MenuItemResponseDto menuItem(
      @PathParam("id") long restaurantId, @PathParam("itemId") long itemId) {
    return service.getMenuItem(restaurantId, itemId);
  }

  @PUT
  @Path("/{id}/menu-items/{itemId}")
  @Consumes(MediaType.APPLICATION_JSON)
  @RolesAllowed("mission-admin")
  public MenuItemResponseDto updateMenuItem(
      @PathParam("id") long restaurantId, @PathParam("itemId") long itemId,
      @Valid MenuItemRequest request) {
    return service.updateMenuItem(restaurantId, itemId, request);
  }

  @DELETE
  @Path("/{id}/menu-items/{itemId}")
  @Authenticated
  public Response deleteMenuItem(
      @PathParam("id") long restaurantId, @PathParam("itemId") long itemId) {
    service.deleteMenuItem(restaurantId, itemId);
    return Response.noContent().build();
  }

  @GET
  @Path("/menu-items/search")
  public MenuItemSearchPageResponseDto searchMenuItems(
      @QueryParam("q") @Size(max = 100) String query,
      @QueryParam("category") @Size(max = 100) String category,
      @QueryParam("available") Boolean available,
      @QueryParam("currency") @jakarta.validation.constraints.Pattern(regexp = "[A-Za-z]{3}") String currency,
      @QueryParam("page") @Min(0) @Max(99) @jakarta.ws.rs.DefaultValue("0") int page,
      @QueryParam("size") @Min(1) @Max(100) @jakarta.ws.rs.DefaultValue("20") int size) {
    return service.searchMenuItems(query, category, available, currency, page, size);
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
      @QueryParam("openNow") Boolean openNow,
      @QueryParam("page") @Min(0) @Max(99) @jakarta.ws.rs.DefaultValue("0") int page,
      @QueryParam("size") @Min(1) @Max(100) @jakarta.ws.rs.DefaultValue("20") int size) {
    return service.search(query, city, cuisine, latitude, longitude, radiusKm, openNow, page, size);
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
  public Response reindex(@Context UriInfo uriInfo) {
    RestaurantReindexResponseDto job = service.queueReindex();
    return Response.accepted()
        .location(uriInfo.getAbsolutePathBuilder().path(job.id().toString()).build())
        .entity(job)
        .build();
  }

  @GET
  @Path("/reindex/{jobId}")
  @RolesAllowed("mission-admin")
  public RestaurantReindexResponseDto getReindex(@PathParam("jobId") long jobId) {
    return service.getReindex(jobId);
  }
}
