package com.busgo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public class PaymentDtos {
  public record IyziBasketItemRequest(
      @NotNull Integer seatNumber,
      @NotNull BigDecimal amount,
      String passengerName) {}

  public record IyziPaymentRequest(
      @NotBlank String cardNumber,
      @NotBlank String cardHolderName,
      @NotBlank String expiry,
      @NotBlank String cvv,
      @NotNull BigDecimal amount,
      String currency,
      @NotEmpty List<IyziBasketItemRequest> items) {}

  public record IyziPaymentItemResponse(
      Integer seatNumber, String paymentTransactionId, BigDecimal amount) {}

  public record IyziPaymentResponse(
      String paymentId,
      String transactionId,
      String status,
      BigDecimal balanceAfter,
      List<IyziPaymentItemResponse> items) {}
}
