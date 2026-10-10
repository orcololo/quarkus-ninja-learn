package org.acme.missions.dto;

import java.time.Instant;

import org.acme.missions.NinjaMissionStatus;

public record NinjaMissionResponseDto(
    Long id,
    Long ninjaId,
    Long missionId,
    String missionTitle,
    int missionPoints,
    NinjaMissionStatus status,
    Instant assignedAt,
    Instant submittedAt,
    Instant decidedAt) {
}
