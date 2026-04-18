package com.busgo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class AdminUserDtos {
  public record CreateAdminRequest(
      @NotBlank String firstName,
      @NotBlank String lastName,
      @NotBlank String companyName,
      @Email @NotBlank String email,
      @NotBlank String password) {}

  public record CreateAdminResponse(
      String email,
      String username,
      String companyName,
      String role) {}
}
