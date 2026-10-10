package org.acme.restaurants.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateRestaurantRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 255) String address,
    @NotBlank @Size(max = 120) String city,
    @NotBlank @Size(max = 100) String cuisine,
    @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
    @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude) {
}
