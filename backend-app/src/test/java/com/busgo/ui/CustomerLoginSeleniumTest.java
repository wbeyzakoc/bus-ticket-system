package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

class CustomerLoginSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc002_emailAndPasswordEmptyShouldShowRequiredValidation() {
    openPage("customer-login.html");

    WebElement form = driver.findElement(By.id("loginForm"));
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    org.junit.jupiter.api.Assertions.assertFalse(checkValidity(form));
    org.junit.jupiter.api.Assertions.assertTrue(isInvalid(driver.findElement(By.id("loginEmail"))));
    org.junit.jupiter.api.Assertions.assertTrue(isInvalid(driver.findElement(By.id("loginPassword"))));
  }

  @Test
  void tc003_emailEmptyPasswordFilledShouldBlockSubmit() {
    openPage("customer-login.html");

    driver.findElement(By.id("loginPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    assertFormAndFieldInvalid("loginForm", "#loginEmail");
  }

  @Test
  void tc004_emailFilledPasswordEmptyShouldBlockSubmit() {
    openPage("customer-login.html");

    driver.findElement(By.id("loginEmail")).sendKeys("ali@example.com");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    assertFormAndFieldInvalid("loginForm", "#loginPassword");
  }

  @Test
  void tc005_unregisteredEmailShouldShowInvalidCredentials() {
    openPage("customer-login.html");
    stubFetchJson("/auth/login", 401, "{\"message\":\"Invalid credentials\"}");

    driver.findElement(By.id("loginEmail")).sendKeys("nobody@example.com");
    driver.findElement(By.id("loginPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Invalid credentials"));
  }

  @Test
  void tc006_registeredEmailWrongPasswordShouldShowInvalidCredentials() {
    openPage("customer-login.html");
    stubFetchJson("/auth/login", 401, "{\"message\":\"Invalid credentials\"}");

    driver.findElement(By.id("loginEmail")).sendKeys("ali@example.com");
    driver.findElement(By.id("loginPassword")).sendKeys("wrong-password");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Invalid credentials"));
  }

  @Test
  void tc007_unregisteredEmailWrongPasswordShouldShowInvalidCredentials() {
    openPage("customer-login.html");
    stubFetchJson("/auth/login", 401, "{\"message\":\"Invalid credentials\"}");

    driver.findElement(By.id("loginEmail")).sendKeys("nobody@example.com");
    driver.findElement(By.id("loginPassword")).sendKeys("wrong-password");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Invalid credentials"));
  }

  @Test
  void tc008_invalidEmailFormatShouldFailNativeValidation() {
    openPage("customer-login.html");

    WebElement email = driver.findElement(By.id("loginEmail"));
    driver.findElement(By.id("loginPassword")).sendKeys("secret123");
    email.sendKeys("abc");

    assertFormAndFieldInvalid("loginForm", "#loginEmail");
  }

  @Test
  void tc009_emailWithSpacesShouldTrimAndLoginSuccessfully() {
    openPage("customer-login.html");
    stubFetchJson("/auth/login", 200, "{\"token\":\"demo-token\",\"user\":{\"username\":\"Ali\",\"email\":\"ali@example.com\",\"role\":\"user\"}}");

    driver.findElement(By.id("loginEmail")).sendKeys("  ali@example.com  ");
    driver.findElement(By.id("loginPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    wait.until(ExpectedConditions.urlContains("index.html"));
    assertTrue(driver.getCurrentUrl().contains("index.html"));
  }

  @Test
  void tc012_sqlInjectionInputShouldNotLogin() {
    openPage("customer-login.html");
    stubFetchJson("/auth/login", 401, "{\"message\":\"Invalid credentials\"}");

    driver.findElement(By.id("loginEmail")).sendKeys("ali@example.com");
    driver.findElement(By.id("loginPassword")).sendKeys("' OR '1'='1");
    driver.findElement(By.cssSelector("#loginForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Invalid credentials"));
  }
}
