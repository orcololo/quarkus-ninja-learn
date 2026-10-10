package org.acme.ninjas.dto;

import org.acme.ninjas.NinjaLevelEnum;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateNinjaRequest(
    @NotBlank @Size(max = 255) String name,
    @NotNull @Min(0) Integer age,
    @NotNull NinjaLevelEnum level,
    @Positive Long villageId) {
}
