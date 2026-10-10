package org.acme.village.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignVillageLeaderRequest(@NotNull @Positive Long ninjaId) {
}
