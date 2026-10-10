package org.acme.restaurants.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MenuItemRequest(
    @NotBlank @Size(max = 160) String name,
    @Size(max = 2000) String description,
    @Size(max = 100) String category,
    @NotNull @DecimalMin("0.000") @Digits(integer = 9, fraction = 3) BigDecimal price,
    @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
    @NotNull Boolean available) {
}
