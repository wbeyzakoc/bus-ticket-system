package com.busgo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats")
public class Seat {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "bus_id")
  private Bus bus;

  @Column(name = "seat_number", nullable = false)
  private Integer seatNumber;

  @Column(name = "row_no", nullable = false)
  private Integer rowNo;

  @Column(name = "col_no", nullable = false)
  private Integer colNo;

  @Column(name = "is_vip", nullable = false)
  private Boolean isVip;

  @Column(nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Bus getBus() {
    return bus;
  }

  public void setBus(Bus bus) {
    this.bus = bus;
  }

  public Integer getSeatNumber() {
    return seatNumber;
  }

  public void setSeatNumber(Integer seatNumber) {
    this.seatNumber = seatNumber;
  }

  public Integer getRowNo() {
    return rowNo;
  }

  public void setRowNo(Integer rowNo) {
    this.rowNo = rowNo;
  }

  public Integer getColNo() {
    return colNo;
  }

  public void setColNo(Integer colNo) {
    this.colNo = colNo;
  }

  public Boolean getIsVip() {
    return isVip;
  }

  public void setIsVip(Boolean isVip) {
    this.isVip = isVip;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
