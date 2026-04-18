package com.busgo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class TripDtos {
  public record TripDto(
      String id,
      String from,
      String to,
      String date,
      String company,
      String departureTime,
      String departureDateTime,
      String duration,
      BigDecimal basePrice,
      String image,
      boolean admin) {}

  public record NearestTripsResponse(String date, java.util.List<TripDto> trips) {}

  public record ChatTripSearchRequest(
      @NotBlank @Size(max = 80) String from,
      @NotBlank @Size(max = 80) String to,
      @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}") String date,
      boolean preferNearest) {}

  public record ChatTripSearchResponse(String requestedDate, String matchType, java.util.List<TripDto> trips) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record TripPayload(
      @NotBlank String id,
      @NotBlank String from,
      @NotBlank String to,
      @NotBlank String date,
      @NotBlank String company,
      @NotBlank String departureTime,
      String departureDateTime,
      @NotNull BigDecimal basePrice,
      String duration,
      String image) {}

  public record AdminTripRequest(
      @NotBlank String from,
      @NotBlank String to,
      @NotBlank String date,
      @NotBlank String departureTime,
      @NotNull BigDecimal basePrice,
      @NotBlank String company) {}
}
