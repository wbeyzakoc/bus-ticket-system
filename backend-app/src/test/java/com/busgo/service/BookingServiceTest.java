package com.busgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busgo.dto.BookingDtos.CancelTicketResponse;
import com.busgo.model.Payment;
import com.busgo.model.PaymentStatus;
import com.busgo.model.Reservation;
import com.busgo.model.Seat;
import com.busgo.model.Ticket;
import com.busgo.model.Trip;
import com.busgo.model.User;
import com.busgo.repo.PaymentRepository;
import com.busgo.repo.ReservationRepository;
import com.busgo.repo.SeatRepository;
import com.busgo.repo.TicketRepository;
import com.busgo.repo.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

class BookingServiceTest {
  @Test
  void buildBookingMailRecipientsIncludesPassengerAndAccountEmails() {
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    ReservationRepository reservationRepository = Mockito.mock(ReservationRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);
    PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
    IyziPaymentService iyziPaymentService = Mockito.mock(IyziPaymentService.class);
    ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    BookingService bookingService =
        new BookingService(
            tripRepository,
            seatRepository,
            reservationRepository,
            ticketRepository,
            paymentRepository,
            iyziPaymentService,
            eventPublisher);

    User user = new User();
    user.setEmail("account@busgo.local");
    user.setUsername("Busgo User");

    Seat seat = new Seat();
    seat.setSeatNumber(12);

    Ticket ticket = new Ticket();
    ticket.setPassengerEmail("passenger@busgo.local");
    ticket.setPassengerName("Passenger One");
    ticket.setSeat(seat);
    ticket.setPrice(BigDecimal.valueOf(90));

    List<BookingConfirmationMailEvent.RecipientBookingMail> recipients =
        bookingService.buildBookingMailRecipients(user, List.of(ticket));

    assertEquals(2, recipients.size());
    BookingConfirmationMailEvent.RecipientBookingMail passengerRecipient =
        recipients.stream()
            .filter(recipient -> "passenger@busgo.local".equals(recipient.email()))
            .findFirst()
            .orElse(null);
    BookingConfirmationMailEvent.RecipientBookingMail accountRecipient =
        recipients.stream()
            .filter(recipient -> "account@busgo.local".equals(recipient.email()))
            .findFirst()
            .orElse(null);

    assertNotNull(passengerRecipient);
    assertNotNull(accountRecipient);
    assertEquals(BigDecimal.valueOf(90), passengerRecipient.total());
    assertEquals(BigDecimal.valueOf(90), accountRecipient.total());
    assertEquals(1, passengerRecipient.tickets().size());
    assertEquals(1, accountRecipient.tickets().size());
  }

  @Test
  void cancelTicketReturnsSandboxRefundMetadata() {
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    ReservationRepository reservationRepository = Mockito.mock(ReservationRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);
    PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
    IyziPaymentService iyziPaymentService = Mockito.mock(IyziPaymentService.class);
    ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    BookingService bookingService =
        new BookingService(
            tripRepository,
            seatRepository,
            reservationRepository,
            ticketRepository,
            paymentRepository,
            iyziPaymentService,
            eventPublisher);

    User user = new User();
    user.setId(UUID.randomUUID());
    user.setEmail("user@busgo.local");
    user.setUsername("Busgo User");

    Reservation reservation = new Reservation();
    reservation.setId(UUID.randomUUID());

    Trip trip = new Trip();
    trip.setDepartureTime(LocalDateTime.now().plusDays(20));

    Seat seat = new Seat();
    seat.setSeatNumber(7);

    Ticket ticket = new Ticket();
    ticket.setId(UUID.randomUUID());
    ticket.setUser(user);
    ticket.setReservation(reservation);
    ticket.setTrip(trip);
    ticket.setSeat(seat);
    ticket.setPrice(BigDecimal.valueOf(350));
    ticket.setCreatedAt(Instant.now());

    Payment originalPayment = new Payment();
    originalPayment.setReservation(reservation);
    originalPayment.setProvider("iyzico");
    originalPayment.setStatus(PaymentStatus.PAID);

    when(ticketRepository.findByIdAndUser(ticket.getId(), user)).thenReturn(Optional.of(ticket));
    when(ticketRepository.findByReservationOrderByCreatedAtAsc(reservation)).thenReturn(List.of(ticket));
    when(paymentRepository.findByReservationOrderByCreatedAtAsc(reservation))
        .thenReturn(List.of(originalPayment));
    when(iyziPaymentService.refundTicket(user, ticket, BigDecimal.valueOf(350)))
        .thenReturn(
            new IyziPaymentService.IyziRefundResult(
                "mock00007iyzihostrfn", BigDecimal.valueOf(5350), false));
    when(reservationRepository.save(any(Reservation.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    CancelTicketResponse response = bookingService.cancelTicket(user, ticket.getId().toString());

    assertEquals("mock00007iyzihostrfn", response.refundReference());
    assertTrue(response.sandboxRefunded());
    assertEquals(BigDecimal.valueOf(5350), response.balanceAfter());
    assertTrue(response.message().contains("iyzico sandbox"));

    ArgumentCaptor<Payment> refundPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
    verify(ticketRepository).delete(ticket);
    verify(paymentRepository).save(refundPaymentCaptor.capture());
    assertEquals(PaymentStatus.REFUNDED, refundPaymentCaptor.getValue().getStatus());
    assertEquals("mock00007iyzihostrfn", refundPaymentCaptor.getValue().getTransactionId());
  }

  @Test
  void listAllTicketsFiltersByScopedAdminsCompany() {
    TripRepository tripRepository = Mockito.mock(TripRepository.class);
    SeatRepository seatRepository = Mockito.mock(SeatRepository.class);
    ReservationRepository reservationRepository = Mockito.mock(ReservationRepository.class);
    TicketRepository ticketRepository = Mockito.mock(TicketRepository.class);
    PaymentRepository paymentRepository = Mockito.mock(PaymentRepository.class);
    IyziPaymentService iyziPaymentService = Mockito.mock(IyziPaymentService.class);
    ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);

    BookingService bookingService =
        new BookingService(
            tripRepository,
            seatRepository,
            reservationRepository,
            ticketRepository,
            paymentRepository,
            iyziPaymentService,
            eventPublisher);

    User admin = new User();
    admin.setCompanyName("Scoped Lines");

    User passenger = new User();
    passenger.setEmail("user@busgo.local");
    passenger.setUsername("Passenger");

    com.busgo.model.BusCompany company = new com.busgo.model.BusCompany();
    company.setName("Scoped Lines");

    com.busgo.model.City fromCity = new com.busgo.model.City();
    fromCity.setName("Istanbul");
    com.busgo.model.City toCity = new com.busgo.model.City();
    toCity.setName("Ankara");

    Trip trip = new Trip();
    trip.setCompany(company);
    trip.setFromCity(fromCity);
    trip.setToCity(toCity);
    trip.setDepartureTime(LocalDateTime.now().plusDays(2));

    Seat seat = new Seat();
    seat.setSeatNumber(5);

    Ticket ticket = new Ticket();
    ticket.setId(UUID.randomUUID());
    ticket.setUser(passenger);
    ticket.setTrip(trip);
    ticket.setSeat(seat);
    ticket.setPassengerName("Passenger");
    ticket.setPrice(BigDecimal.valueOf(200));
    ticket.setCreatedAt(Instant.now());

    when(ticketRepository.findByTrip_Company_NameIgnoreCaseOrderByCreatedAtDesc("Scoped Lines"))
        .thenReturn(List.of(ticket));

    assertEquals(1, bookingService.listAllTickets(admin).size());
    verify(ticketRepository).findByTrip_Company_NameIgnoreCaseOrderByCreatedAtDesc("Scoped Lines");
  }
}
