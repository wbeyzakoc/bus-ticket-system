package com.busgo.service;

import com.busgo.dto.BookingDtos.BookingRequest;
import com.busgo.dto.BookingDtos.BookingResponse;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingService {
  private static final BigDecimal VIP_SURCHARGE = BigDecimal.valueOf(0.4);

  private final TripRepository tripRepository;
  private final SeatRepository seatRepository;
  private final ReservationRepository reservationRepository;
  private final TicketRepository ticketRepository;
  private final PaymentRepository paymentRepository;

  public BookingService(
      TripRepository tripRepository,
      SeatRepository seatRepository,
      ReservationRepository reservationRepository,
      TicketRepository ticketRepository,
      PaymentRepository paymentRepository) {
    this.tripRepository = tripRepository;
    this.seatRepository = seatRepository;
    this.reservationRepository = reservationRepository;
    this.ticketRepository = ticketRepository;
    this.paymentRepository = paymentRepository;
  }

  @Transactional
  public BookingResponse createBooking(User user, BookingRequest request) {
    if (request.trip() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trip details missing");
    }
    if (request.passengers() == null || request.passengers().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Passenger list is required");
    }

    UUID tripId = parseUuid(request.trip().id());
    Trip trip = tripRepository.findById(tripId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Trip not found"));
    if (trip.getStatus() != null && trip.getStatus() != TripStatus.SCHEDULED) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Trip is not available");
    }

    Set<Integer> seatSet = new HashSet<>();
    List<TicketDto> created = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;

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
      ticket.setCreatedAt(Instant.now());
      ticketRepository.save(ticket);

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
    if (request.paymentRef() != null && !request.paymentRef().isBlank()) {
      payment.setTransactionId(request.paymentRef().trim());
    }
    payment.setPaidAt(Instant.now());
    payment.setCreatedAt(Instant.now());
    paymentRepository.save(payment);

    return new BookingResponse(created, total);
  }

  public List<TicketDto> listTickets(User user) {
    return ticketRepository.findByUser(user).stream().map(this::toDto).toList();
  }

  public List<TicketDto> listAllTickets() {
    return ticketRepository.findAll().stream().map(this::toDto).toList();
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

  private TicketDto toDto(Ticket ticket) {
    return new TicketDto(
        ticket.getId().toString(),
        ticket.getUser().getEmail(),
        ticket.getUser().getUsername(),
        ticket.getTrip().getFromCity().getName(),
        ticket.getTrip().getToCity().getName(),
        ticket.getTrip().getDepartureTime().toLocalDate().toString(),
        String.valueOf(ticket.getSeat().getSeatNumber()),
        ticket.getPassengerName(),
        ticket.getPrice(),
        ticket.getTrip().getCompany().getName(),
        ticket.getCreatedAt().toEpochMilli());
  }

  private UUID parseUuid(String id) {
    try {
      return UUID.fromString(id);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid trip id");
    }
  }
}
