package org.acme.restaurants.dto;

import java.math.BigDecimal;

public record MenuItemResponseDto(
    Long id,
    Long restaurantId,
    String name,
    String description,
    String category,
    BigDecimal price,
    String currency,
    boolean available) {
}
