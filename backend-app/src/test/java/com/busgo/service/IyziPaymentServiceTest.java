package com.busgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busgo.model.Seat;
import com.busgo.model.Ticket;
import com.busgo.model.User;
import com.busgo.repo.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class IyziPaymentServiceTest {
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void refundTicketUsesPaymentDetailWhenItemTransactionIdIsMissing() throws Exception {
    AtomicReference<String> refundRequestBody = new AtomicReference<>("");
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/payment/detail",
        exchange -> {
          String response =
              """
              {
                "status":"success",
                "paymentId":"25168803",
                "itemTransactions":[
                  {
                    "itemId":"seat-12",
                    "paymentTransactionId":"27137212"
                  }
                ]
              }
              """;
          writeResponse(exchange, response);
        });
    server.createContext(
        "/payment/refund",
        exchange -> {
          refundRequestBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          String response =
              """
              {
                "status":"success",
                "paymentId":"25168803",
                "paymentTransactionId":"27137212",
                "refundHostReference":"mock00007iyzihostrfn"
              }
              """;
          writeResponse(exchange, response);
        });
    server.start();

    UserRepository userRepository = Mockito.mock(UserRepository.class);
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    IyziPaymentService service =
        new IyziPaymentService(
            new ObjectMapper(),
            userRepository,
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "sandbox-key",
            "sandbox-secret",
            BigDecimal.valueOf(5000));

    User user = new User();
    user.setDemoBalance(BigDecimal.valueOf(100));

    Seat seat = new Seat();
    seat.setSeatNumber(12);

    Ticket ticket = new Ticket();
    ticket.setSeat(seat);
    ticket.setProviderPaymentId("25168803");
    ticket.setProviderPaymentTransactionId("");

    IyziPaymentService.IyziRefundResult result =
        service.refundTicket(user, ticket, BigDecimal.TEN);

    assertEquals("mock00007iyzihostrfn", result.reference());
    assertFalse(result.localFallback());
    assertEquals(BigDecimal.valueOf(110), result.balanceAfter());
    assertTrue(refundRequestBody.get().contains("\"paymentTransactionId\":\"27137212\""));
    verify(userRepository, atLeastOnce()).save(user);
  }

  private void writeResponse(com.sun.net.httpserver.HttpExchange exchange, String response)
      throws IOException {
    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    try (OutputStream outputStream = exchange.getResponseBody()) {
      outputStream.write(bytes);
    }
  }
}
