package org.acme.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
    @NotBlank @Size(min = 3, max = 64) @Pattern(regexp = "[a-zA-Z0-9._-]+") String username,
    @NotBlank @Email @Size(max = 254) String email,
    @NotBlank @Size(min = 12, max = 128) String password) {
}
