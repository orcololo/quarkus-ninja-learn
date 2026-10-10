package org.acme.restaurants.dto;

public record MenuItemSearchResultDto(
    MenuItemResponseDto item,
    RestaurantResponseDto restaurant,
    DisplayPriceDto displayPrice) {
}
