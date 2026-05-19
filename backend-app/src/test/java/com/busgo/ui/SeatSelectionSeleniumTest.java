package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class SeatSelectionSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc063_continueWithoutSeatShouldShowValidationError() {
    prepareSeatsPage();

    driver.findElement(By.id("continueBtn")).click();

    assertTrue(waitForToastText().contains("All passengers must have name, age, and assigned seat."));
  }

  @Test
  void tc064_availableSeatShouldBecomeSelected() {
    prepareSeatsPage();

    driver.findElement(By.cssSelector("button[data-seat='5']")).click();

    assertTrue(pageSourceContains("Seat: 5"));
    assertTrue(summaryText().contains("Selected: 5"));
  }

  @Test
  void tc066_clickingSelectedSeatAgainShouldRemoveSelection() {
    prepareSeatsPage();

    driver.findElement(By.cssSelector("button[data-seat='5']")).click();
    driver.findElement(By.cssSelector("button[data-seat='5']")).click();

    assertTrue(pageSourceContains("Seat: not assigned"));
    assertTrue(summaryText().contains("Selected: -"));
  }

  @Test
  void tc069_vipSeatShouldIncreaseTotal() {
    prepareSeatsPage();

    driver.findElement(By.cssSelector("button[data-seat='1']")).click();

    assertTrue(summaryText().contains("Selected: 1"));
    assertTrue(summaryText().contains("Total: ₺630,00"));
  }

  @Test
  void tc070_totalShouldUpdateBasedOnSeatChoice() {
    prepareSeatsPage();

    driver.findElement(By.cssSelector("button[data-seat='5']")).click();
    assertTrue(summaryText().contains("Total: ₺450,00"));
    driver.findElement(By.cssSelector("button[data-seat='5']")).click();
    driver.findElement(By.cssSelector("button[data-seat='1']")).click();
    assertTrue(summaryText().contains("Total: ₺630,00"));
  }

  @Test
  void tc067_multiplePassengersShouldGetDifferentSeats() {
    openPage("seats.html");
    setLocalStorage("busgo_search", "{\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\",\"ticketCount\":2}");
    setLocalStorage("busgo_trip", "{\"id\":\"trip-1\",\"company\":\"BusGo Express\",\"departureTime\":\"09:30\",\"duration\":\"6h 15m\",\"basePrice\":450,"
        + "\"departureDateTime\":\"2030-01-15T09:30:00\"}");
    setLocalStorage("busgo_passengers_draft", "[{\"firstName\":\"Ali\",\"lastName\":\"Yilmaz\",\"name\":\"Ali Yilmaz\",\"tc\":\"12345678901\","
        + "\"age\":25,\"email\":\"ali@example.com\",\"phone\":\"+90(555) 111 22 33\",\"gender\":\"male\",\"baggage\":15,\"seatNumber\":null},"
        + "{\"firstName\":\"Ayse\",\"lastName\":\"Kaya\",\"name\":\"Ayse Kaya\",\"tc\":\"12345678902\","
        + "\"age\":24,\"email\":\"ayse@example.com\",\"phone\":\"+90(555) 123 45 67\",\"gender\":\"female\",\"baggage\":15,\"seatNumber\":null}]");
    setLocalStorage("busgo_user", "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    openPage("seats.html");

    driver.findElement(By.cssSelector("button[data-seat='5']")).click();
    driver.findElement(By.cssSelector("button[data-activate='1']")).click();
    driver.findElement(By.cssSelector("button[data-seat='6']")).click();

    assertTrue(pageSourceContains("Seat: 5"));
    assertTrue(pageSourceContains("Seat: 6"));
    assertTrue(summaryText().contains("Selected: 5, 6"));
  }

  @Test
  void tc068_sameSeatCannotBeAssignedToSecondPassenger() {
    openPage("seats.html");
    setLocalStorage("busgo_search", "{\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\",\"ticketCount\":2}");
    setLocalStorage("busgo_trip", "{\"id\":\"trip-1\",\"company\":\"BusGo Express\",\"departureTime\":\"09:30\",\"duration\":\"6h 15m\",\"basePrice\":450,"
        + "\"departureDateTime\":\"2030-01-15T09:30:00\"}");
    setLocalStorage("busgo_passengers_draft", "[{\"firstName\":\"Ali\",\"lastName\":\"Yilmaz\",\"name\":\"Ali Yilmaz\",\"tc\":\"12345678901\","
        + "\"age\":25,\"email\":\"ali@example.com\",\"phone\":\"+90(555) 111 22 33\",\"gender\":\"male\",\"baggage\":15,\"seatNumber\":null},"
        + "{\"firstName\":\"Ayse\",\"lastName\":\"Kaya\",\"name\":\"Ayse Kaya\",\"tc\":\"12345678902\","
        + "\"age\":24,\"email\":\"ayse@example.com\",\"phone\":\"+90(555) 123 45 67\",\"gender\":\"female\",\"baggage\":15,\"seatNumber\":null}]");
    setLocalStorage("busgo_user", "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    openPage("seats.html");

    driver.findElement(By.cssSelector("button[data-seat='5']")).click();
    driver.findElement(By.cssSelector("button[data-activate='1']")).click();
    driver.findElement(By.cssSelector("button[data-seat='5']")).click();

    assertEquals(1, driver.findElements(By.cssSelector(".seat.selected")).size());
    assertTrue(summaryText().contains("Selected: 5"));
  }
}
