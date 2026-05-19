package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class PaymentSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc073_cardNumberEmptyShouldShowRequiredValidation() {
    preparePaymentPage();
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardExpiry", "12/30");
    setInputValue("#cardCvv", "123");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertFormAndFieldInvalid("paymentForm", "#cardNumber");
  }

  @Test
  void tc074_cardNumberShortShouldShowFormatToast() {
    preparePaymentPage();
    setInputValue("#cardNumber", "1234");
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardExpiry", "12/30");
    setInputValue("#cardCvv", "123");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Card number must be 16 digits."));
  }

  @Test
  void tc075_cardNumberLettersShouldShowFormatToast() {
    preparePaymentPage();
    setInputValue("#cardNumber", "abcd");
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardExpiry", "12/30");
    setInputValue("#cardCvv", "123");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Card number must be 16 digits."));
  }

  @Test
  void tc076_cardHolderEmptyShouldShowRequiredValidation() {
    preparePaymentPage();
    setInputValue("#cardNumber", "5528790000000008");
    setInputValue("#cardExpiry", "12/30");
    setInputValue("#cardCvv", "123");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertFormAndFieldInvalid("paymentForm", "#cardName");
  }

  @Test
  void tc077_expiryEmptyShouldShowRequiredValidation() {
    preparePaymentPage();
    setInputValue("#cardNumber", "5528790000000008");
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardCvv", "123");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertFormAndFieldInvalid("paymentForm", "#cardExpiry");
  }

  @Test
  void tc079_cvvEmptyShouldShowRequiredValidation() {
    preparePaymentPage();
    setInputValue("#cardNumber", "5528790000000008");
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardExpiry", "12/30");
    setInputValue("#cardCvv", "");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertFormAndFieldInvalid("paymentForm", "#cardCvv");
  }

  @Test
  void tc080_cvvShortShouldShowFormatToast() {
    preparePaymentPage();
    setInputValue("#cardNumber", "5528790000000008");
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardExpiry", "12/30");
    setInputValue("#cardCvv", "1");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertFormAndFieldInvalid("paymentForm", "#cardCvv");
  }

  @Test
  void tc081_expiryShouldAutoInsertSlash() {
    preparePaymentPage();

    setInputValue("#cardExpiry", "1230");

    assertEquals("12/30", driver.findElement(By.id("cardExpiry")).getAttribute("value"));
  }

  @Test
  void tc082_cvvShouldNotAcceptMoreThanThreeDigits() {
    preparePaymentPage();

    setInputValue("#cardCvv", "1234");

    assertEquals("123", driver.findElement(By.id("cardCvv")).getAttribute("value"));
  }

  @Test
  void tc083_expiredExpiryDateShouldShowValidationToast() {
    preparePaymentPage();
    setInputValue("#cardNumber", "5528790000000008");
    setInputValue("#cardName", "Ali Yilmaz");
    setInputValue("#cardExpiry", "01/20");
    setInputValue("#cardCvv", "123");

    driver.findElement(By.cssSelector("#paymentForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Expiry date cannot be in the past."));
  }
}
