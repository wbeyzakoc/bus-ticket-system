package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class TripListSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc041_noTripsShouldShowEmptyState() {
    prepareTripsPage("[]", "{\"trips\":[]}");

    assertTrue(pageSourceContains("Aradığınız tarihte seferimiz yoktur."));
  }

  @Test
  void tc044_firstTripShouldRenderSelectableCard() {
    prepareTripsPage(
        "[{\"id\":\"trip-1\",\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\",\"departureTime\":\"09:30\",\"duration\":\"6h 15m\",\"company\":\"BusGo Express\",\"basePrice\":450}]",
        "{\"trips\":[]}");
    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
        "renderTrips(arguments[0], document.getElementById('tripList'));",
        java.util.List.of(java.util.Map.of(
            "id", "trip-1",
            "from", "Ankara",
            "to", "Izmir",
            "date", "2030-01-15",
            "departureTime", "09:30",
            "duration", "6h 15m",
            "company", "BusGo Express",
            "basePrice", 450)));
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#tripList button[data-id='trip-1']")));

    assertTrue(driver.findElement(By.cssSelector("#tripList button[data-id='trip-1']")).isDisplayed());
    assertTrue(pageSourceContains("BusGo Express"));
  }
}
