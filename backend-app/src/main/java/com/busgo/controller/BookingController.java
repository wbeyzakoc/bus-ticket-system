package com.busgo.controller;

import com.busgo.dto.BookingDtos.BookingRequest;
import com.busgo.dto.BookingDtos.BookingResponse;
import com.busgo.dto.BookingDtos.CancelTicketResponse;
import com.busgo.dto.BookingDtos.TicketDto;
import com.busgo.model.User;
import com.busgo.service.AuthService;
import com.busgo.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {
  private final AuthService authService;
  private final BookingService bookingService;

  public BookingController(AuthService authService, BookingService bookingService) {
    this.authService = authService;
    this.bookingService = bookingService;
  }

  @PostMapping("/bookings")
  public BookingResponse createBooking(@Valid @RequestBody BookingRequest request, HttpServletRequest httpRequest) {
    User user = authService.requireUser(httpRequest);
    return bookingService.createBooking(user, request);
  }

  @GetMapping("/tickets/me")
  public List<TicketDto> listMyTickets(HttpServletRequest request) {
    User user = authService.requireUser(request);
    return bookingService.listTickets(user);
  }

  @DeleteMapping("/tickets/{ticketId}")
  public CancelTicketResponse cancelTicket(
      @PathVariable String ticketId, HttpServletRequest request) {
    User user = authService.requireUser(request);
    return bookingService.cancelTicket(user, ticketId);
  }
}
