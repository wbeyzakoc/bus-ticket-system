package com.busgo.service;

import java.math.BigDecimal;
import java.util.List;

public record BookingConfirmationMailEvent(
    List<RecipientBookingMail> recipients,
    String route,
    String departureDateTime,
    String company,
    String bookingReference) {
  public record RecipientBookingMail(
      String email,
      String userName,
      BigDecimal total,
      List<TicketMailLine> tickets) {}

  public record TicketMailLine(
      String passengerName,
      String seatNumber,
      BigDecimal price) {}
}
