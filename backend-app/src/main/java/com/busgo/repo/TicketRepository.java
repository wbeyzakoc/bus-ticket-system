package com.busgo.repo;

import com.busgo.model.Reservation;
import com.busgo.model.Ticket;
import com.busgo.model.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
  List<Ticket> findByUser(User user);
  List<Ticket> findByReservationOrderByCreatedAtAsc(Reservation reservation);
  List<Ticket> findByTrip_Id(UUID tripId);
  Optional<Ticket> findByIdAndUser(UUID id, User user);
  boolean existsByTrip_IdAndSeat_Id(UUID tripId, UUID seatId);
}
