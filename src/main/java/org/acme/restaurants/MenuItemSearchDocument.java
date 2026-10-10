package org.acme.restaurants;

import java.math.BigDecimal;

record MenuItemSearchDocument(
    long id,
    long restaurantId,
    String name,
    String description,
    String category,
    BigDecimal price,
    String currency,
    boolean available) {
  static MenuItemSearchDocument from(MenuItemEntity item) {
    return new MenuItemSearchDocument(
        item.getId(),
        item.getRestaurantId(),
        item.getName(),
        item.getDescription(),
        item.getCategory(),
        item.getPrice(),
        item.getCurrency(),
        item.isAvailable());
  }
}
