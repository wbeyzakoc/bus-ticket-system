package com.busgo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AuthDtos {
  public record LoginRequest(
      @Email @NotBlank String email,
      @NotBlank String password,
      String role) {}

  public record RegisterRequest(
      @NotBlank String username,
      @Email @NotBlank String email,
      @NotBlank String password,
      String role) {}

  public record AuthResponse(String token, UserDto user) {}
}
