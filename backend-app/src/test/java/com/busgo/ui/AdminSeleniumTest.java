package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;

class AdminSeleniumTest extends BaseFrontendSeleniumTest {

  @Test
  void tc092_normalUserShouldNotAccessAdminPage() {
    prepareLoggedInUserPage("admin.html");

    wait.until(ExpectedConditions.urlContains("index.html"));
    assertTrue(driver.getCurrentUrl().contains("index.html"));
  }

  @Test
  void tc094_companyNameRequiredShouldShowValidationError() {
    prepareAdminUserPage("admin.html");
    stubFetchJson("/admin/cities", 200, "[]");
    stubFetchJson("/admin/companies", 200, "[]");
    stubFetchJson("/admin/trips", 200, "[]");
    stubFetchJson("/admin/tickets", 200, "[]");
    openPage("admin.html");

    driver.findElement(By.cssSelector("#adminCompanyForm button[type='submit']")).click();

    assertFormAndFieldInvalid("adminCompanyForm", "#adminCompanyName");
  }

  @Test
  void tc097_tripFormEmptyShouldShowValidationError() {
    prepareAdminUserPage("admin.html");
    stubFetchJson("/admin/cities", 200, "[]");
    stubFetchJson("/admin/companies", 200, "[]");
    stubFetchJson("/admin/trips", 200, "[]");
    stubFetchJson("/admin/tickets", 200, "[]");
    openPage("admin.html");

    driver.findElement(By.cssSelector("#adminTripForm button[type='submit']")).click();

    assertFormAndFieldInvalid("adminTripForm", "#adminDate");
  }

  @Test
  void tc093_adminPageShouldOpenForAdminUser() {
    prepareAdminUserPage("admin.html");
    stubFetchJson("/admin/cities", 200, "[]");
    stubFetchJson("/admin/companies", 200, "[]");
    stubFetchJson("/admin/trips", 200, "[]");
    stubFetchJson("/admin/tickets", 200, "[]");
    openPage("admin.html");

    assertTrue(pageSourceContains("Manage Cities"));
    assertTrue(pageSourceContains("Create / Edit Trip"));
  }
}
