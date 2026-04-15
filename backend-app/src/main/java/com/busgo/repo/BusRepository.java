package com.busgo.repo;

import com.busgo.model.Bus;
import com.busgo.model.BusCompany;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusRepository extends JpaRepository<Bus, UUID> {
  Optional<Bus> findFirstByCompanyOrderByCreatedAtAsc(BusCompany company);
}
