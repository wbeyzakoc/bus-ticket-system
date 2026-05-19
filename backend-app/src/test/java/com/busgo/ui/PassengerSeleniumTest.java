package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class PassengerSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc048_firstNameEmptyShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".first-name", "");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertTrue(waitForToastText().contains("Please fix passenger form errors before continuing."));
    assertTrue(pageSourceContains("First name is required"));
  }

  @Test
  void tc050_tcEmptyShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".tc-no", "");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertTrue(waitForToastText().contains("Please fix passenger form errors before continuing."));
    assertTrue(pageSourceContains("TC is required"));
  }

  @Test
  void tc049_lastNameEmptyShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".last-name", "");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertTrue(waitForToastText().contains("Please fix passenger form errors before continuing."));
    assertTrue(pageSourceContains("Last name is required"));
  }

  @Test
  void tc051_tcShortShouldShowFormatError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".tc-no", "12345");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertFormAndFieldInvalid("passengerForm", ".tc-no");
  }

  @Test
  void tc052_tcLettersShouldBeRejected() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".tc-no", "abc");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertTrue(waitForToastText().contains("Please fix passenger form errors before continuing."));
    assertTrue(pageSourceContains("TC is required"));
  }

  @Test
  void tc054_ageEmptyShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".age", "");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertTrue(waitForToastText().contains("Please fix passenger form errors before continuing."));
  }

  @Test
  void tc056_ageAboveHundredShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".age", "101");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertFormAndFieldInvalid("passengerForm", ".age");
  }

  @Test
  void tc057_invalidPassengerEmailShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".email", "test@");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertFormAndFieldInvalid("passengerForm", ".email");
  }

  @Test
  void tc058_invalidPassengerPhoneShouldShowValidationError() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".phone", "123");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    assertTrue(waitForToastText().contains("Please fix passenger form errors before continuing."));
    assertTrue(pageSourceContains("Invalid phone format"));
  }

  @Test
  void tc059_validInternationalPhoneShouldProceedToSeats() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    setInputValue(".phone", "+90(555) 111 22 33");

    driver.findElement(By.id("continueToSeatsBtn")).click();

    wait.until((ignored) -> driver.getCurrentUrl().contains("seats.html"));
    assertTrue(driver.getCurrentUrl().contains("seats.html"));
  }

  @Test
  void tc062_removePassengerShouldReorderCards() {
    preparePassengerPage();
    fillPassengerWithValidDefaults();
    driver.findElement(By.id("addPassengerBtn")).click();
    driver.findElement(By.cssSelector(".passenger-input-card:nth-of-type(2) [data-remove-passenger='1']")).click();

    assertEquals(1, driver.findElements(By.cssSelector(".passenger-input-card")).size());
    assertTrue(pageSourceContains("Passenger 1"));
  }
}
