package com.busgo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trips")
public class Trip {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "company_id")
  private BusCompany company;

  @ManyToOne(optional = false)
  @JoinColumn(name = "bus_id")
  private Bus bus;

  @ManyToOne(optional = false)
  @JoinColumn(name = "from_city_id")
  private City fromCity;

  @ManyToOne(optional = false)
  @JoinColumn(name = "to_city_id")
  private City toCity;

  @Column(name = "departure_time", nullable = false)
  private LocalDateTime departureTime;

  @Column(name = "arrival_time")
  private LocalDateTime arrivalTime;

  @Column(name = "duration_minutes")
  private Integer durationMinutes;

  @Column(name = "base_price", nullable = false, scale = 2, precision = 10)
  private BigDecimal basePrice;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private TripStatus status;

  @Column(nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public BusCompany getCompany() {
    return company;
  }

  public void setCompany(BusCompany company) {
    this.company = company;
  }

  public Bus getBus() {
    return bus;
  }

  public void setBus(Bus bus) {
    this.bus = bus;
  }

  public City getFromCity() {
    return fromCity;
  }

  public void setFromCity(City fromCity) {
    this.fromCity = fromCity;
  }

  public City getToCity() {
    return toCity;
  }

  public void setToCity(City toCity) {
    this.toCity = toCity;
  }

  public LocalDateTime getDepartureTime() {
    return departureTime;
  }

  public void setDepartureTime(LocalDateTime departureTime) {
    this.departureTime = departureTime;
  }

  public LocalDateTime getArrivalTime() {
    return arrivalTime;
  }

  public void setArrivalTime(LocalDateTime arrivalTime) {
    this.arrivalTime = arrivalTime;
  }

  public Integer getDurationMinutes() {
    return durationMinutes;
  }

  public void setDurationMinutes(Integer durationMinutes) {
    this.durationMinutes = durationMinutes;
  }

  public BigDecimal getBasePrice() {
    return basePrice;
  }

  public void setBasePrice(BigDecimal basePrice) {
    this.basePrice = basePrice;
  }

  public TripStatus getStatus() {
    return status;
  }

  public void setStatus(TripStatus status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
