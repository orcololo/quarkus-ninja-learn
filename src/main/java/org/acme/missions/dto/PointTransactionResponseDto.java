package org.acme.missions.dto;

import java.time.Instant;

public record PointTransactionResponseDto(
    Long id,
    Long ninjaId,
    Long ninjaMissionId,
    int points,
    Instant grantedAt) {
}
