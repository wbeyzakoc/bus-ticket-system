package com.busgo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tickets")
public class Ticket {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "reservation_id")
  private Reservation reservation;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id")
  private User user;

  @ManyToOne(optional = false)
  @JoinColumn(name = "trip_id")
  private Trip trip;

  @ManyToOne(optional = false)
  @JoinColumn(name = "seat_id")
  private Seat seat;

  @Column(nullable = false)
  private String passengerName;

  @Column(nullable = false)
  private String passengerTc;

  @Column(nullable = false)
  private Integer passengerAge;

  @Column(nullable = false)
  private String passengerEmail;

  @Column(nullable = false)
  private String passengerPhone;

  @Column(nullable = false)
  private String passengerGender;

  @Column(nullable = false)
  private Integer passengerBaggage;

  @Column(nullable = false, scale = 2, precision = 10)
  private BigDecimal price;

  @Column(name = "provider_payment_id")
  private String providerPaymentId;

  @Column(name = "provider_payment_transaction_id")
  private String providerPaymentTransactionId;

  @Column(nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Reservation getReservation() {
    return reservation;
  }

  public void setReservation(Reservation reservation) {
    this.reservation = reservation;
  }

  public User getUser() {
    return user;
  }

  public void setUser(User user) {
    this.user = user;
  }

  public Trip getTrip() {
    return trip;
  }

  public void setTrip(Trip trip) {
    this.trip = trip;
  }

  public Seat getSeat() {
    return seat;
  }

  public void setSeat(Seat seat) {
    this.seat = seat;
  }

  public String getPassengerName() {
    return passengerName;
  }

  public void setPassengerName(String passengerName) {
    this.passengerName = passengerName;
  }

  public String getPassengerTc() {
    return passengerTc;
  }

  public void setPassengerTc(String passengerTc) {
    this.passengerTc = passengerTc;
  }

  public Integer getPassengerAge() {
    return passengerAge;
  }

  public void setPassengerAge(Integer passengerAge) {
    this.passengerAge = passengerAge;
  }

  public String getPassengerEmail() {
    return passengerEmail;
  }

  public void setPassengerEmail(String passengerEmail) {
    this.passengerEmail = passengerEmail;
  }

  public String getPassengerPhone() {
    return passengerPhone;
  }

  public void setPassengerPhone(String passengerPhone) {
    this.passengerPhone = passengerPhone;
  }

  public String getPassengerGender() {
    return passengerGender;
  }

  public void setPassengerGender(String passengerGender) {
    this.passengerGender = passengerGender;
  }

  public Integer getPassengerBaggage() {
    return passengerBaggage;
  }

  public void setPassengerBaggage(Integer passengerBaggage) {
    this.passengerBaggage = passengerBaggage;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal price) {
    this.price = price;
  }

  public String getProviderPaymentId() {
    return providerPaymentId;
  }

  public void setProviderPaymentId(String providerPaymentId) {
    this.providerPaymentId = providerPaymentId;
  }

  public String getProviderPaymentTransactionId() {
    return providerPaymentTransactionId;
  }

  public void setProviderPaymentTransactionId(String providerPaymentTransactionId) {
    this.providerPaymentTransactionId = providerPaymentTransactionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
