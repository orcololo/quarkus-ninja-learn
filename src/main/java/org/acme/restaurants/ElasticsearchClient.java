package org.acme.restaurants;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/")
@RegisterRestClient(configKey = "elasticsearch")
@RegisterProvider(ElasticsearchAuthFilter.class)
public interface ElasticsearchClient {
  @HEAD
  @Path("/{index}")
  Response indexExists(@PathParam("index") String index);

  @PUT
  @Path("/{index}")
  @Consumes(MediaType.APPLICATION_JSON)
  Response createIndex(@PathParam("index") String index, String mapping);

  @PUT
  @Path("/{index}/_mapping")
  @Consumes(MediaType.APPLICATION_JSON)
  Response updateMapping(@PathParam("index") String index, String mapping);

  @POST
  @Path("/{index}/_bulk")
  @Consumes("application/x-ndjson")
  @Produces(MediaType.APPLICATION_JSON)
  Response bulk(@PathParam("index") String index, String operations);

  @POST
  @Path("/{index}/_search")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  Response search(@PathParam("index") String index, String query);
}
