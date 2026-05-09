package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;

class ForgotPasswordSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc028_emptyEmailShouldShowValidationToast() {
    openPage("customer-login.html");

    driver.findElement(By.id("loginForgotToggle")).click();
    driver.findElement(By.cssSelector("#loginForgotForm button[type='submit']")).click();

    assertFormAndFieldInvalid("loginForgotForm", "#loginForgotEmail");
  }

  @Test
  void tc029_invalidEmailFormatShouldShowNativeValidation() {
    openPage("customer-login.html");

    driver.findElement(By.id("loginForgotToggle")).click();
    driver.findElement(By.id("loginForgotEmail")).sendKeys("abc");
    driver.findElement(By.cssSelector("#loginForgotForm button[type='submit']")).click();

    assertFormAndFieldInvalid("loginForgotForm", "#loginForgotEmail");
  }

  @Test
  void tc030_unregisteredEmailShouldShowSafeErrorMessage() {
    openPage("customer-login.html");
    stubFetchJson("/auth/forgot-password", 404, "{\"message\":\"Password reset failed.\"}");

    driver.findElement(By.id("loginForgotToggle")).click();
    driver.findElement(By.id("loginForgotEmail")).sendKeys("nobody@example.com");
    driver.findElement(By.cssSelector("#loginForgotForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Password reset failed."));
  }
}
