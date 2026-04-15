package com.busgo.controller;

import com.busgo.dto.PaymentDtos.IyziPaymentRequest;
import com.busgo.dto.PaymentDtos.IyziPaymentResponse;
import com.busgo.model.User;
import com.busgo.service.AuthService;
import com.busgo.service.IyziPaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
  private final AuthService authService;
  private final IyziPaymentService iyziPaymentService;

  public PaymentController(AuthService authService, IyziPaymentService iyziPaymentService) {
    this.authService = authService;
    this.iyziPaymentService = iyziPaymentService;
  }

  @PostMapping("/iyzico")
  public IyziPaymentResponse createIyziPayment(
      @Valid @RequestBody IyziPaymentRequest request, HttpServletRequest httpRequest) {
    User user = authService.requireUser(httpRequest);
    return iyziPaymentService.authorizePayment(user, request, httpRequest.getRemoteAddr());
  }
}
