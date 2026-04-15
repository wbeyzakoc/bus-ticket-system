package com.busgo.repo;

import com.busgo.model.BusCompany;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BusCompanyRepository extends JpaRepository<BusCompany, UUID> {
  Optional<BusCompany> findByNameIgnoreCase(String name);
}
