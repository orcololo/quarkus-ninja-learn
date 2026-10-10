package org.acme.ninjas.dto;

import org.acme.ninjas.NinjaLevelEnum;

public record NinjaResponseDto(
    Long id,
    String name,
    int age,
    NinjaLevelEnum level,
    VillageReferenceDto village) {
}
