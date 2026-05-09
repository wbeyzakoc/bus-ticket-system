package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class RegisterSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc016_successfulRegistrationShouldRedirectToHome() {
    openPage("register.html");
    stubFetchJson("/auth/register", 200, "{\"token\":\"demo-token\",\"user\":{\"username\":\"New User\",\"email\":\"new@example.com\",\"role\":\"user\"}}");

    driver.findElement(By.id("registerName")).sendKeys("New User");
    driver.findElement(By.id("registerEmail")).sendKeys("new@example.com");
    driver.findElement(By.id("registerPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    wait.until(ExpectedConditions.urlContains("index.html"));
    assertTrue(driver.getCurrentUrl().contains("index.html"));
  }

  @Test
  void tc019_emptyEmailShouldShowRequiredValidation() {
    openPage("register.html");

    driver.findElement(By.id("registerName")).sendKeys("New User");
    driver.findElement(By.id("registerPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertFormAndFieldInvalid("registerForm", "#registerEmail");
  }

  @Test
  void tc017_emptyNameShouldShowRequiredValidation() {
    openPage("register.html");

    driver.findElement(By.id("registerEmail")).sendKeys("new@example.com");
    driver.findElement(By.id("registerPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertFormAndFieldInvalid("registerForm", "#registerName");
  }

  @Test
  void tc020_emptyPasswordShouldShowRequiredValidation() {
    openPage("register.html");

    driver.findElement(By.id("registerName")).sendKeys("New User");
    driver.findElement(By.id("registerEmail")).sendKeys("new@example.com");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertFormAndFieldInvalid("registerForm", "#registerPassword");
  }

  @Test
  void tc021_allFieldsEmptyShouldShowRequiredValidation() {
    openPage("register.html");

    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertFormAndFieldInvalid("registerForm", "#registerName");
  }

  @Test
  void tc023_invalidEmailShouldShowNativeValidation() {
    openPage("register.html");

    driver.findElement(By.id("registerName")).sendKeys("New User");
    driver.findElement(By.id("registerEmail")).sendKeys("abc");
    driver.findElement(By.id("registerPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertFormAndFieldInvalid("registerForm", "#registerEmail");
  }

  @Test
  void tc024_shortPasswordShouldShowValidationToast() {
    openPage("register.html");

    driver.findElement(By.id("registerName")).sendKeys("New User");
    driver.findElement(By.id("registerEmail")).sendKeys("new@example.com");
    driver.findElement(By.id("registerPassword")).sendKeys("12345");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Password must be at least 6 characters."));
  }

  @Test
  void tc022_duplicateEmailShouldShowRegistrationError() {
    openPage("register.html");
    stubFetchJson("/auth/register", 409, "{\"message\":\"Registration failed.\"}");

    driver.findElement(By.id("registerName")).sendKeys("Existing User");
    driver.findElement(By.id("registerEmail")).sendKeys("ali@example.com");
    driver.findElement(By.id("registerPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    assertTrue(waitForToastText().contains("Registration failed."));
  }

  @Test
  void tc026_emailWithSpacesShouldTrimAndRegister() {
    openPage("register.html");
    stubFetchJson("/auth/register", 200, "{\"token\":\"demo-token\",\"user\":{\"username\":\"Space User\",\"email\":\"space@example.com\",\"role\":\"user\"}}");

    driver.findElement(By.id("registerName")).sendKeys("Space User");
    driver.findElement(By.id("registerEmail")).sendKeys("  space@example.com  ");
    driver.findElement(By.id("registerPassword")).sendKeys("secret123");
    driver.findElement(By.cssSelector("#registerForm button[type='submit']")).click();

    wait.until(ExpectedConditions.urlContains("index.html"));
    assertTrue(driver.getCurrentUrl().contains("index.html"));
  }
}
