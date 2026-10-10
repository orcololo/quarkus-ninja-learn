package org.acme.restaurants;

import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@ApplicationScoped
public class ElasticsearchAuthFilter implements ClientRequestFilter {
  @ConfigProperty(name = "elasticsearch.api-key")
  Optional<String> apiKey;

  @Override
  public void filter(ClientRequestContext requestContext) {
    apiKey.filter(key -> !key.isBlank())
        .ifPresent(key -> requestContext.getHeaders().putSingle("Authorization", "ApiKey " + key));
  }
}
