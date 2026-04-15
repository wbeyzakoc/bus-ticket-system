package com.busgo.service;

import com.busgo.dto.BookingDtos.BookingRequest;
import com.busgo.dto.BookingDtos.BookingResponse;
import com.busgo.dto.BookingDtos.CancelTicketResponse;
import com.busgo.dto.BookingDtos.PaymentItemPayload;
import com.busgo.dto.BookingDtos.PassengerPayload;
import com.busgo.dto.BookingDtos.TicketDto;
import com.busgo.model.Payment;
import com.busgo.model.PaymentStatus;
import com.busgo.model.Reservation;
import com.busgo.model.ReservationStatus;
import com.busgo.model.Seat;
import com.busgo.model.Ticket;
import com.busgo.model.Trip;
import com.busgo.model.TripStatus;
import com.busgo.model.User;
import com.busgo.repo.PaymentRepository;
import com.busgo.repo.ReservationRepository;
import com.busgo.repo.SeatRepository;
import com.busgo.repo.TicketRepository;
import com.busgo.repo.TripRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {
  private static final BigDecimal VIP_SURCHARGE = BigDecimal.valueOf(0.4);
  private static final long CANCELLATION_NOTICE_DAYS = 15;
  private static final String CANCELLATION_BLOCKED_MESSAGE =
      "Tickets can only be cancelled more than 15 days before departure.";

  private final TripRepository tripRepository;
  private final SeatRepository seatRepository;
  private final ReservationRepository reservationRepository;
  private final TicketRepository ticketRepository;
  private final PaymentRepository paymentRepository;
  private final IyziPaymentService iyziPaymentService;
  private final ApplicationEventPublisher eventPublisher;

  public BookingService(
      TripRepository tripRepository,
      SeatRepository seatRepository,
      ReservationRepository reservationRepository,
      TicketRepository ticketRepository,
      PaymentRepository paymentRepository,
      IyziPaymentService iyziPaymentService,
      ApplicationEventPublisher eventPublisher) {
    this.tripRepository = tripRepository;
    this.seatRepository = seatRepository;
    this.reservationRepository = reservationRepository;
    this.ticketRepository = ticketRepository;
    this.paymentRepository = paymentRepository;
    this.iyziPaymentService = iyziPaymentService;
    this.eventPublisher = eventPublisher;
  }

  @Transactional
  public BookingResponse createBooking(User user, BookingRequest request) {
    if (request.trip() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip details missing");
    }
    if (request.passengers() == null || request.passengers().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passenger list is required");
    }

    UUID tripId = parseUuid(request.trip().id(), "trip");
    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    if (trip.getStatus() != null && trip.getStatus() != TripStatus.SCHEDULED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip is not available");
    }

    Set<Integer> seatSet = new HashSet<>();
    List<TicketDto> created = new ArrayList<>();
    List<Ticket> createdTickets = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    Map<Integer, PaymentItemPayload> paymentItemsBySeat = mapPaymentItems(request.paymentItems());

    Reservation reservation = new Reservation();
    reservation.setUser(user);
    reservation.setTrip(trip);
    reservation.setStatus(ReservationStatus.CONFIRMED);
    reservation.setCreatedAt(Instant.now());
    reservation.setTotalAmount(BigDecimal.ZERO);
    reservationRepository.save(reservation);

    for (PassengerPayload passenger : request.passengers()) {
      Integer seatNumber = passenger.seatNumber();
      if (seatNumber == null || seatNumber < 1) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid seat selection");
      }
      if (!seatSet.add(seatNumber)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate seat selection");
      }

      Seat seat = seatRepository
          .findByBusAndSeatNumber(trip.getBus(), seatNumber)
          .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seat not found"));

      if (ticketRepository.existsByTrip_IdAndSeat_Id(tripId, seat.getId())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "Seat " + seatNumber + " already booked");
      }

      BigDecimal price = computePrice(trip.getBasePrice(), seat);
      PaymentItemPayload paymentItem = paymentItemsBySeat.get(seatNumber);
      Ticket ticket = new Ticket();
      ticket.setReservation(reservation);
      ticket.setUser(user);
      ticket.setTrip(trip);
      ticket.setSeat(seat);
      ticket.setPassengerName(resolveName(passenger));
      ticket.setPassengerTc(valueOrEmpty(passenger.tc()));
      ticket.setPassengerAge(passenger.age() != null ? passenger.age() : 0);
      ticket.setPassengerEmail(valueOrEmpty(passenger.email()));
      ticket.setPassengerPhone(valueOrEmpty(passenger.phone()));
      ticket.setPassengerGender(valueOrEmpty(passenger.gender()));
      ticket.setPassengerBaggage(passenger.baggage() != null ? passenger.baggage() : 15);
      ticket.setPrice(price);
      ticket.setProviderPaymentId(valueOrEmpty(request.paymentId()));
      ticket.setProviderPaymentTransactionId(paymentItem == null ? "" : valueOrEmpty(paymentItem.paymentTransactionId()));
      ticket.setCreatedAt(Instant.now());
      ticketRepository.save(ticket);

      createdTickets.add(ticket);
      created.add(toDto(ticket));
      total = total.add(price);
    }

    reservation.setTotalAmount(total);
    reservationRepository.save(reservation);

    Payment payment = new Payment();
    payment.setReservation(reservation);
    payment.setAmount(total);
    String provider = request.paymentProvider() == null || request.paymentProvider().isBlank() ? "demo" : request.paymentProvider().trim();
    payment.setProvider(provider);
    payment.setStatus(PaymentStatus.PAID);
    String paymentReference =
        request.paymentId() != null && !request.paymentId().isBlank()
            ? request.paymentId().trim()
            : valueOrEmpty(request.paymentRef());
    if (!paymentReference.isBlank()) {
      payment.setTransactionId(paymentReference);
    }
    payment.setPaidAt(Instant.now());
    payment.setCreatedAt(Instant.now());
    paymentRepository.save(payment);

    publishBookingMailEvent(user, trip, createdTickets, paymentReference, reservation);

    return new BookingResponse(created, total);
  }

  public List<TicketDto> listTickets(User user) {
    return ticketRepository.findByUser(user).stream().map(this::toDto).toList();
  }

  public List<TicketDto> listAllTickets() {
    return ticketRepository.findAll().stream().map(this::toDto).toList();
  }

  @Transactional
  public CancelTicketResponse cancelTicket(User user, String ticketId) {
    Ticket ticket =
        ticketRepository
            .findByIdAndUser(parseUuid(ticketId, "ticket"), user)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));

    if (!canCancelTicket(ticket)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, CANCELLATION_BLOCKED_MESSAGE);
    }

    Reservation reservation = ticket.getReservation();
    List<Ticket> reservationTickets =
        ticketRepository.findByReservationOrderByCreatedAtAsc(reservation);
    Payment originalPayment = findOriginalPayment(reservation);

    BigDecimal refundedAmount = ticket.getPrice();
    BigDecimal remainingTotal =
        reservationTickets.stream()
            .filter(existing -> !existing.getId().equals(ticket.getId()))
            .map(Ticket::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal balanceAfter = null;
    String refundReference = "RFND-" + UUID.randomUUID().toString().replace("-", "");
    if (originalPayment != null && "iyzico".equalsIgnoreCase(originalPayment.getProvider())) {
      IyziPaymentService.IyziRefundResult refundResult =
          iyziPaymentService.refundTicket(user, ticket, refundedAmount);
      balanceAfter = refundResult.balanceAfter();
      refundReference = refundResult.reference();
    } else {
      balanceAfter = iyziPaymentService.creditDemoBalance(user, refundedAmount);
      refundReference = "LOCAL-REFUND-" + UUID.randomUUID();
    }

    ticketRepository.delete(ticket);

    reservation.setTotalAmount(remainingTotal);
    reservation.setStatus(
        remainingTotal.signum() == 0 ? ReservationStatus.CANCELLED : ReservationStatus.CONFIRMED);
    reservationRepository.save(reservation);

    Payment refund = new Payment();
    refund.setReservation(reservation);
    refund.setAmount(refundedAmount);
    refund.setProvider(resolvePaymentProvider(reservation));
    refund.setStatus(PaymentStatus.REFUNDED);
    refund.setTransactionId(refundReference);
    refund.setPaidAt(Instant.now());
    refund.setCreatedAt(Instant.now());
    paymentRepository.save(refund);

    return new CancelTicketResponse(
        ticket.getId().toString(),
        refundedAmount,
        balanceAfter,
        "Ticket cancelled successfully.");
  }

  private BigDecimal computePrice(BigDecimal basePrice, Seat seat) {
    if (seat.getIsVip() != null && seat.getIsVip()) {
      return basePrice.add(basePrice.multiply(VIP_SURCHARGE));
    }
    return basePrice;
  }

  private String resolveName(PassengerPayload passenger) {
    if (passenger == null) return "Passenger";
    if (passenger.name() != null && !passenger.name().isBlank()) return passenger.name();
    String first = passenger.firstName() == null ? "" : passenger.firstName().trim();
    String last = passenger.lastName() == null ? "" : passenger.lastName().trim();
    String combined = (first + " " + last).trim();
    return combined.isBlank() ? "Passenger" : combined;
  }

  private String valueOrEmpty(String value) {
    return value == null ? "" : value.trim();
  }

  private Map<Integer, PaymentItemPayload> mapPaymentItems(List<PaymentItemPayload> paymentItems) {
    Map<Integer, PaymentItemPayload> mapped = new HashMap<>();
    if (paymentItems == null) return mapped;
    for (PaymentItemPayload item : paymentItems) {
      if (item == null || item.seatNumber() == null) continue;
      mapped.put(item.seatNumber(), item);
    }
    return mapped;
  }

  private void publishBookingMailEvent(
      User user,
      Trip trip,
      List<Ticket> createdTickets,
      String paymentReference,
      Reservation reservation) {
    if (createdTickets == null || createdTickets.isEmpty()) return;

    Map<String, List<Ticket>> groupedByEmail = new HashMap<>();
    for (Ticket ticket : createdTickets) {
      String email = normalizeEmail(ticket.getPassengerEmail());
      if (email.isBlank()) {
        email = normalizeEmail(user.getEmail());
      }
      if (email.isBlank()) continue;
      groupedByEmail.computeIfAbsent(email, key -> new ArrayList<>()).add(ticket);
    }
    if (groupedByEmail.isEmpty()) return;

    List<BookingConfirmationMailEvent.RecipientBookingMail> recipients = new ArrayList<>();
    for (Map.Entry<String, List<Ticket>> entry : groupedByEmail.entrySet()) {
      List<BookingConfirmationMailEvent.TicketMailLine> lines = new ArrayList<>();
      BigDecimal recipientTotal = BigDecimal.ZERO;
      for (Ticket ticket : entry.getValue()) {
        lines.add(
            new BookingConfirmationMailEvent.TicketMailLine(
                ticket.getPassengerName(),
                String.valueOf(ticket.getSeat().getSeatNumber()),
                ticket.getPrice()));
        recipientTotal = recipientTotal.add(ticket.getPrice());
      }
      recipients.add(
          new BookingConfirmationMailEvent.RecipientBookingMail(
              entry.getKey(),
              user.getUsername(),
              recipientTotal,
              lines));
    }

    String bookingReference =
        !valueOrEmpty(paymentReference).isBlank()
            ? paymentReference
            : reservation.getId() == null ? "" : reservation.getId().toString();
    eventPublisher.publishEvent(
        new BookingConfirmationMailEvent(
            recipients,
            trip.getFromCity().getName() + " -> " + trip.getToCity().getName(),
            trip.getDepartureTime().toString(),
            trip.getCompany().getName(),
            bookingReference));
  }

  private TicketDto toDto(Ticket ticket) {
    boolean cancellable = canCancelTicket(ticket);
    return new TicketDto(
        ticket.getId().toString(),
        ticket.getUser().getEmail(),
        ticket.getUser().getUsername(),
        ticket.getTrip().getFromCity().getName(),
        ticket.getTrip().getToCity().getName(),
        ticket.getTrip().getDepartureTime().toLocalDate().toString(),
        ticket.getTrip().getDepartureTime().toString(),
        String.valueOf(ticket.getSeat().getSeatNumber()),
        ticket.getPassengerName(),
        ticket.getPrice(),
        ticket.getTrip().getCompany().getName(),
        cancellable,
        cancellable ? null : CANCELLATION_BLOCKED_MESSAGE,
        ticket.getCreatedAt().toEpochMilli());
  }

  private boolean canCancelTicket(Ticket ticket) {
    return ticket.getTrip().getDepartureTime().isAfter(LocalDateTime.now().plusDays(CANCELLATION_NOTICE_DAYS));
  }

  private String resolvePaymentProvider(Reservation reservation) {
    Payment original = findOriginalPayment(reservation);
    if (original != null && original.getProvider() != null && !original.getProvider().isBlank()) {
      return original.getProvider();
    }
    return "system";
  }

  private Payment findOriginalPayment(Reservation reservation) {
    return paymentRepository.findByReservationOrderByCreatedAtAsc(reservation).stream()
        .filter(payment -> payment.getStatus() == PaymentStatus.PAID)
        .findFirst()
        .orElse(null);
  }

  private UUID parseUuid(String id, String label) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + label + " id");
    }
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.US);
  }
}
