package com.busgo.repo;

import com.busgo.model.Reservation;
import java.util.List;
import com.busgo.model.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  List<Payment> findByReservationOrderByCreatedAtAsc(Reservation reservation);
}
