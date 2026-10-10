package org.acme.village.dto;

import org.acme.village.VillageTypeEnum;

public record VillageResponseDto(
    Long id,
    String name,
    String location,
    String description,
    int population,
    VillageTypeEnum type,
    NinjaReferenceDto leader) {
}
