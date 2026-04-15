package com.busgo.repo;

import com.busgo.model.City;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CityRepository extends JpaRepository<City, UUID> {
  Optional<City> findByNameIgnoreCase(String name);
}
