package org.acme.users.dto;

import java.time.Instant;

public record DeliveryAddressResponseDto(
    Long id,
    String label,
    String recipientName,
    String phone,
    String street,
    String number,
    String complement,
    String neighborhood,
    String city,
    String state,
    String postalCode,
    String country,
    String deliveryInstructions,
    boolean isDefault,
    Instant createdAt) {
}
