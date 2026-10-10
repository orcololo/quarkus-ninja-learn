package org.acme.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeliveryAddressRequest(
    @NotBlank @Size(max = 60) String label,
    @NotBlank @Size(max = 120) String recipientName,
    @NotBlank @Pattern(regexp = "\\+?[0-9() .-]{7,25}") String phone,
    @NotBlank @Size(max = 160) String street,
    @NotBlank @Size(max = 30) String number,
    @Size(max = 120) String complement,
    @NotBlank @Size(max = 100) String neighborhood,
    @NotBlank @Size(max = 120) String city,
    @NotBlank @Size(max = 100) String state,
    @NotBlank @Size(max = 20) String postalCode,
    @NotBlank @Pattern(regexp = "[A-Za-z]{2}") String country,
    @Size(max = 500) String deliveryInstructions) {
}
