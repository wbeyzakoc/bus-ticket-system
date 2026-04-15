package com.busgo.repo;

import com.busgo.model.Bus;
import com.busgo.model.Seat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatRepository extends JpaRepository<Seat, UUID> {
  List<Seat> findByBus(Bus bus);
  Optional<Seat> findByBusAndSeatNumber(Bus bus, Integer seatNumber);
}
