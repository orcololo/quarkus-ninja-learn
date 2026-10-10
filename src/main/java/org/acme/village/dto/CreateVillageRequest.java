package org.acme.village.dto;

import org.acme.village.VillageTypeEnum;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateVillageRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String location,
    @Size(max = 255) String description,
    @NotNull VillageTypeEnum type,
    @Positive Long leaderId) {
}
