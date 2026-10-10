package org.acme.restaurants.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OpeningHoursDto(
    @NotBlank @Size(max = 64) String timeZone,
    @NotNull @Size(max = 56) List<@Valid OpeningPeriodDto> periods) {
}
