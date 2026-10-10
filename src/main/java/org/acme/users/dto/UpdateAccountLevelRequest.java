package org.acme.users.dto;

import org.acme.users.AccountLevel;

import jakarta.validation.constraints.NotNull;

public record UpdateAccountLevelRequest(@NotNull AccountLevel level) {
}
