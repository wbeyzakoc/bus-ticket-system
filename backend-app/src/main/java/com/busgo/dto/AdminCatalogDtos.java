package com.busgo.dto;

import jakarta.validation.constraints.NotBlank;

public class AdminCatalogDtos {
  public record CityDto(String id, String name, String countryCode) {}

  public record CityRequest(@NotBlank String name, String countryCode) {}

  public record CompanyDto(String id, String name, String phone, String email, String logoUrl) {}

  public record CompanyRequest(@NotBlank String name, String phone, String email, String logoUrl) {}
}
