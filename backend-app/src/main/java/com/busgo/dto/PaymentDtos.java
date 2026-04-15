package com.busgo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PaymentDtos {
  public record IyziPaymentRequest(
      @NotBlank String cardNumber,
      @NotBlank String cardHolderName,
      @NotBlank String expiry,
      @NotBlank String cvv,
      @NotNull BigDecimal amount,
      String currency) {}

  public record IyziPaymentResponse(String transactionId, String status) {}
}
