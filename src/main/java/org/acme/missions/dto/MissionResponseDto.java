package org.acme.missions.dto;

public record MissionResponseDto(
    Long id,
    String title,
    String description,
    int points,
    boolean active) {
}
