package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class SearchSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc034_originRequiredShouldBlockSearch() {
    openPage("index.html");

    selectCity("toCity", "Izmir");
    setDateValue("travelDate", LocalDate.now().toString());
    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    assertFormAndFieldInvalid("searchForm", "#fromCity");
  }

  @Test
  void tc035_destinationRequiredShouldBlockSearch() {
    openPage("index.html");

    selectCity("fromCity", "Ankara");
    setDateValue("travelDate", LocalDate.now().toString());
    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    assertFormAndFieldInvalid("searchForm", "#toCity");
  }

  @Test
  void tc036_dateRequiredShouldBlockSearch() {
    openPage("index.html");

    selectCity("fromCity", "Ankara");
    selectCity("toCity", "Izmir");
    WebElement date = driver.findElement(By.id("travelDate"));
    setDateValue("travelDate", "");
    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    assertTrue(isInvalid(date));
  }

  @Test
  void tc037_allFieldsEmptyShouldBlockSearch() {
    openPage("index.html");

    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    assertFormAndFieldInvalid("searchForm", "#fromCity");
  }

  @Test
  void tc038_sameOriginAndDestinationShouldShowErrorToast() {
    openPage("index.html");

    selectCity("fromCity", "Istanbul");
    selectCity("toCity", "Istanbul");
    setDateValue("travelDate", LocalDate.now().toString());

    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Origin and destination cannot be the same."));
  }

  @Test
  void tc040_validRouteShouldOpenTripsPage() {
    openPage("index.html");

    selectCity("fromCity", "Ankara");
    selectCity("toCity", "Izmir");
    setDateValue("travelDate", LocalDate.now().toString());

    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    wait.until(ExpectedConditions.urlContains("trips.html"));
    assertTrue(driver.getCurrentUrl().contains("trips.html"));
  }

  @Test
  void tc042_cityDropdownsShouldContainOptions() {
    openPage("index.html");

    assertTrue(driver.findElements(By.cssSelector("#fromCity option")).size() > 1);
    assertTrue(driver.findElements(By.cssSelector("#toCity option")).size() > 1);
  }

  @Test
  void tc039_pastDateShouldBeInvalidAgainstMinDate() {
    openPage("index.html");

    selectCity("fromCity", "Ankara");
    selectCity("toCity", "Izmir");
    setDateValue("travelDate", "2020-01-01");
    driver.findElement(By.cssSelector("#searchForm button[type='submit']")).click();

    assertTrue(isInvalid(driver.findElement(By.id("travelDate"))));
  }
}
