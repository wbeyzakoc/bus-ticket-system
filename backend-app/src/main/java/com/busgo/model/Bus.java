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
@Table(name = "buses")
public class Bus {
  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "company_id")
  private BusCompany company;

  @Column(name = "plate_no", nullable = false, unique = true)
  private String plateNo;

  private String model;

  @Column(name = "seat_rows", nullable = false)
  private Integer seatRows;

  @Column(name = "seat_cols", nullable = false)
  private Integer seatCols;

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

  public String getPlateNo() {
    return plateNo;
  }

  public void setPlateNo(String plateNo) {
    this.plateNo = plateNo;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Integer getSeatRows() {
    return seatRows;
  }

  public void setSeatRows(Integer seatRows) {
    this.seatRows = seatRows;
  }

  public Integer getSeatCols() {
    return seatCols;
  }

  public void setSeatCols(Integer seatCols) {
    this.seatCols = seatCols;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
