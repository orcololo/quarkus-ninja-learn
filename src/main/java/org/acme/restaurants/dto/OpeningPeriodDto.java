package org.acme.restaurants.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;

public record OpeningPeriodDto(
    @NotNull DayOfWeek dayOfWeek,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
    @NotNull @JsonFormat(pattern = "HH:mm") LocalTime closesAt) {
}
