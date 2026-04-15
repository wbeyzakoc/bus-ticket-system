package com.busgo.repo;

import com.busgo.model.Ticket;
import com.busgo.model.User;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {
  List<Ticket> findByUser(User user);
  List<Ticket> findByTrip_Id(UUID tripId);
  boolean existsByTrip_IdAndSeat_Id(UUID tripId, UUID seatId);
}
