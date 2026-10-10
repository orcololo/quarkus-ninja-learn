package org.acme.users.dto;

import java.util.List;

import org.acme.ninjas.dto.NinjaResponseDto;
import org.acme.users.AccountLevel;

public record GameUserResponseDto(
    Long id,
    String username,
    String email,
    AccountLevel level,
    Integer ninjaLimit,
    List<NinjaResponseDto> ninjas) {
}
