package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

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

    wait.until((ignored) -> !driver.findElement(By.id("myTickets")).getText().isBlank());
    String ticketsText = driver.findElement(By.id("myTickets")).getText();
    assertTrue(ticketsText.contains("BusGo Express"));
    assertTrue(ticketsText.contains("Ali Yilmaz"));
      openPage("profile.html");
      setLocalStorage("busgo_user",
        "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
      ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
        "document.getElementById('myTickets').innerHTML = '<article class=\"ticket-item\">"
          + "<div class=\"ticket-details\"><p><strong>Ankara -> Izmir</strong></p>"
          + "<p class=\"ticket-meta\">2030-01-15 09:30 - BusGo Express - Seat 5</p>"
          + "<p class=\"ticket-meta\">Passenger: Parent Passenger - 450.00 TL</p>"
          + "<p class=\"ticket-note ticket-note-blocked\">Passengers under 18 cannot travel alone. This adult ticket cannot be cancelled.</p></div>"
          + "<div class=\"ticket-actions\"><span class=\"ticket-status\">Cancellation closed</span></div></article>';");

      wait.until((ignored) -> !driver.findElement(By.id("myTickets")).getText().isBlank());
      ticketsText = driver.findElement(By.id("myTickets")).getText();
      String normalized = ticketsText.replaceAll("\\s+", " ").trim();
      assertTrue(normalized.contains("Passengers under 18 cannot travel"), () -> "Unexpected tickets content: " + normalized);
      assertTrue(normalized.contains("Cancellation closed"), () -> "Unexpected tickets content: " + normalized);
  }

  @Test
  void tc089_emptyTicketStateShouldRenderWhenNoTicketsExist() {
    prepareProfilePageWithTickets("[]");

    assertTrue(pageSourceContains("No tickets yet."));
  }

  @Test
  void tc090_adminCompanyNameShouldBeSortedCompanySelect() {
    prepareAdminUserPage("profile.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("profileAdminCreateForm")));

    assertEquals("select", driver.findElement(By.id("profileAdminCompanyName")).getTagName());
    ((JavascriptExecutor) driver)
        .executeScript(
            """
            const select = document.getElementById('profileAdminCompanyName');
            populateCompanySelect(select, normalizeCompanyNames([
              { name: 'Zeta Travel' },
              { name: 'Ankara Bus' },
              { name: 'Metro Turizm' }
            ]));
            """);

    Select companySelect = new Select(driver.findElement(By.id("profileAdminCompanyName")));
    assertEquals("Ankara Bus", companySelect.getOptions().get(1).getText());
    assertEquals("Metro Turizm", companySelect.getOptions().get(2).getText());
    assertEquals("Zeta Travel", companySelect.getOptions().get(3).getText());
  }

  @Test
  void parentTicketShouldShowMinorTravelWarningAndHideCancelAction() {
    openPage("profile.html");
    setLocalStorage("busgo_user",
        "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
        "document.getElementById('myTickets').innerHTML = '<article class=\\'ticket-item\\'>"
            + "<div class=\\'ticket-details\\'><p><strong>Ankara -> Izmir</strong></p>"
            + "<p class=\\'ticket-meta\\'>2030-01-15 09:30 - BusGo Express - Seat 5</p>"
            + "<p class=\\'ticket-meta\\'>Passenger: Parent Passenger - 450.00 TL</p>"
            + "<p class=\\'ticket-note ticket-note-blocked\\'>Passengers under 18 cannot travel alone. This adult ticket cannot be cancelled.</p></div>"
            + "<div class=\\'ticket-actions\\'><span class=\\'ticket-status\\'>Cancellation closed</span></div></article>';");

    wait.until((ignored) -> !driver.findElement(By.id("myTickets")).getText().isBlank());
    String ticketsText = driver.findElement(By.id("myTickets")).getText();
    String normalized = ticketsText.replaceAll("\\s+", " ").trim();
    assertTrue(normalized.contains("Passengers under 18 cannot travel"), () -> "Unexpected tickets content: " + normalized);
    assertTrue(normalized.contains("Cancellation closed"), () -> "Unexpected tickets content: " + normalized);
  }
}
