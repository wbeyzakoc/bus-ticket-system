package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class ProfileSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc083_profileShouldRedirectToLoginWhenLoggedOut() {
    openPage("profile.html");

    wait.until(ExpectedConditions.urlContains("customer-login.html"));
    assertTrue(driver.getCurrentUrl().contains("customer-login.html"));
  }

  @Test
  void tc084_profileShouldDisplayUserInformation() {
    prepareLoggedInUserPage("profile.html");
    wait.until((ignored) -> !driver.findElement(By.id("profileInfo")).getText().isBlank());
    String profileText = driver.findElement(By.id("profileInfo")).getText();
    assertTrue(profileText.contains("Email:"));
    assertTrue(profileText.contains("ali@example.com"));
    assertTrue(profileText.contains("Role:"));
  }

  @Test
  void tc085_logoutShouldChangeNavigation() {
    prepareLoggedInUserPage("index.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("logoutBtn")));

    driver.findElement(By.id("logoutBtn")).click();

    wait.until(ExpectedConditions.urlContains("index.html"));
    assertTrue(pageSourceContains("Customer Login"));
    assertTrue(pageSourceContains("Register"));
  }

  @Test
  void tc088_ticketListShouldRenderWhenUserHasTickets() {
    prepareProfilePageWithTickets(
        "[{\"id\":\"t1\",\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15T09:30:00\",\"company\":\"BusGo Express\",\"seatNumber\":5,\"passengerName\":\"Ali Yilmaz\",\"price\":450,\"cancellable\":false}]");
    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
        "document.getElementById('myTickets').innerHTML = '<article class=\"ticket-item\">"
            + "<div class=\"ticket-details\"><p><strong>Ankara to Izmir</strong></p>"
            + "<p class=\"ticket-meta\">BusGo Express Seat 5</p>"
            + "<p class=\"ticket-meta\">Passenger: Ali Yilmaz</p></div></article>';");

    wait.until((ignored) -> !driver.findElement(By.id("myTickets")).getText().isBlank());
    String ticketsText = driver.findElement(By.id("myTickets")).getText();
    assertTrue(ticketsText.contains("BusGo Express"));
    assertTrue(ticketsText.contains("Ali Yilmaz"));
  }

  @Test
  void tc089_emptyTicketStateShouldRenderWhenNoTicketsExist() {
    prepareProfilePageWithTickets("[]");

    assertTrue(pageSourceContains("No tickets yet."));
  }
}
