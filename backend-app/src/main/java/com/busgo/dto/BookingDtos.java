package com.busgo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class BookingDtos {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PassengerPayload(
      String name,
      String firstName,
      String lastName,
      String tc,
      Integer age,
      String email,
      String phone,
      String gender,
      Integer baggage,
      Integer seatNumber) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record SearchPayload(String from, String to, String date, Integer ticketCount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record PaymentItemPayload(Integer seatNumber, String paymentTransactionId, BigDecimal amount) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record BookingRequest(
      @NotNull @Valid TripDtos.TripPayload trip,
      @NotEmpty @Valid List<PassengerPayload> passengers,
      SearchPayload search,
      BigDecimal total,
      String paymentProvider,
      String paymentRef,
      String paymentId,
      @Valid List<PaymentItemPayload> paymentItems) {}

  public record TicketDto(
      String id,
      String userEmail,
      String userName,
      String from,
      String to,
      String date,
      String departureDateTime,
      String seatNumber,
      String passengerName,
      BigDecimal price,
      String company,
      boolean cancellable,
      String cancellationMessage,
      long createdAt) {}

  public record CancelTicketResponse(
      String id, BigDecimal refundedAmount, BigDecimal balanceAfter, String message) {}

  public record BookingResponse(List<TicketDto> tickets, BigDecimal total) {}
}
