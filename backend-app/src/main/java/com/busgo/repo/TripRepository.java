package com.busgo.repo;

import com.busgo.model.City;
import com.busgo.model.Trip;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TripRepository extends JpaRepository<Trip, UUID> {
  List<Trip> findByFromCityAndToCityAndDepartureTimeBetween(
      City fromCity,
      City toCity,
      LocalDateTime start,
      LocalDateTime end);
}
