package com.busgo.controller;

import com.busgo.dto.PaymentDtos.IyziPaymentRequest;
import com.busgo.dto.PaymentDtos.IyziPaymentResponse;
import com.busgo.model.User;
import com.busgo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final AuthService authService;

  public PaymentController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/iyzico")
  public IyziPaymentResponse createIyziPayment(@Valid @RequestBody IyziPaymentRequest request, HttpServletRequest httpRequest) {
    User user = authService.requireUser(httpRequest);
    if (user == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required");
    }

    String cardNumber = request.cardNumber().replaceAll("\\s+", "");
    if (!cardNumber.matches("^\\d{16}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card number");
    }
    if (!request.expiry().matches("^\\d{2}/\\d{2}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid expiry");
    }
    if (!request.cvv().matches("^\\d{3,4}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CVV");
    }

    String transactionId = "IYZ-" + UUID.randomUUID().toString();
    return new IyziPaymentResponse(transactionId, "success");
  }
}
