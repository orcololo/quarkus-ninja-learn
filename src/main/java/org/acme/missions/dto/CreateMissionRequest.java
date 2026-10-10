package org.acme.missions.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateMissionRequest(
    @NotBlank @Size(max = 120) String title,
    @Size(max = 1000) String description,
    @NotNull @Min(1) Integer points) {
}
