package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

class AdminSeleniumTest extends BaseFrontendSeleniumTest {
  private static final String VALID_COMPANY_PHONE = "+90(212) 555 01 23";

  @Test
  void tc092_normalUserShouldNotAccessAdminPage() {
    prepareLoggedInUserPage("admin.html");

    wait.until(ExpectedConditions.urlContains("index.html"));
    assertTrue(driver.getCurrentUrl().contains("index.html"));
  }

  @Test
  void tc093_adminPageShouldOpenForAdminUser() {
    prepareAdminValidationPage();

    assertTrue(pageSourceContains("Manage Cities"));
    assertTrue(pageSourceContains("Create / Edit Trip"));
  }

  @Test
  void tc094_cityNameRequiredShouldBlockSubmit() {
    prepareAdminValidationPage();
    fillCityForm("", "TR");

    submit("#adminCityForm");

    assertFormAndFieldInvalid("adminCityForm", "#adminCityName");
    assertEquals(0, recordedPostCount("/admin/cities"));
  }

  @Test
  void tc095_cityCountryCodeOptionalShouldAllowSubmit() {
    prepareAdminValidationPage();
    fillCityForm("Konya", "");

    assertTrue(checkValidity(driver.findElement(By.id("adminCityForm"))));
    assertTrue(checkValidity(driver.findElement(By.id("adminCityCountry"))));
  }

  @ParameterizedTest(name = "{0} empty")
  @MethodSource("companyRequiredFields")
  void tc096_companyRequiredFieldEmptyShouldBlockSubmit(String label, String selector) {
    prepareAdminValidationPage();
    fillCompanyForm("Metro Turizm", VALID_COMPANY_PHONE, "ops@metro.test", "https://example.test/logo.png");
    clearField(selector);

    submit("#adminCompanyForm");

    assertFormAndFieldInvalid("adminCompanyForm", selector);
    assertEquals(0, recordedPostCount("/admin/companies"), label + " should block company creation");
  }

  @Test
  void tc097_companyLogoUrlOptionalShouldAllowSubmit() {
    prepareAdminValidationPage();
    fillCompanyForm("Metro Turizm", VALID_COMPANY_PHONE, "ops@metro.test", "");

    submit("#adminCompanyForm");

    wait.until((ignored) -> recordedPostCount("/admin/companies") == 1);
    assertTrue(checkValidity(driver.findElement(By.id("adminCompanyLogo"))));
  }

  @Test
  void tc098_companyPhoneInvalidFormatShouldBlockSubmit() {
    prepareAdminValidationPage();
    fillCompanyForm("Metro Turizm", VALID_COMPANY_PHONE, "ops@metro.test", "https://example.test/logo.png");
    setElementValue(By.id("adminCompanyPhone"), "123");

    submit("#adminCompanyForm");

    assertFormAndFieldInvalid("adminCompanyForm", "#adminCompanyPhone");
    assertEquals(0, recordedPostCount("/admin/companies"));
  }

  @Test
  void tc099_tripFormAllRequiredFieldsEmptyShouldBlockSubmit() {
    prepareAdminValidationPage();

    submit("#adminTripForm");

    assertFormAndFieldInvalid("adminTripForm", "#adminFrom");
    assertFormAndFieldInvalid("adminTripForm", "#adminTo");
    assertFormAndFieldInvalid("adminTripForm", "#adminDate");
    assertFormAndFieldInvalid("adminTripForm", "#adminTime");
    assertFormAndFieldInvalid("adminTripForm", "#adminPrice");
    assertFormAndFieldInvalid("adminTripForm", "#adminCompany");
    assertEquals(0, recordedPostCount("/admin/trips"));
  }

  @ParameterizedTest(name = "{0} empty")
  @MethodSource("tripRequiredFields")
  void tc100_tripRequiredFieldEmptyShouldBlockSubmit(String label, String selector) {
    prepareAdminValidationPage();
    fillTripFormWithValidDefaults();
    clearField(selector);

    submit("#adminTripForm");

    assertFormAndFieldInvalid("adminTripForm", selector);
    assertEquals(0, recordedPostCount("/admin/trips"), label + " should block trip creation");
  }

  @Test
  void tc101_tripPastDateShouldBlockSubmit() {
    prepareAdminValidationPage();
    fillTripFormWithValidDefaults();
    setElementValue(By.id("adminDate"), "2020-01-01");

    submit("#adminTripForm");

    assertFormAndFieldInvalid("adminTripForm", "#adminDate");
    assertEquals(0, recordedPostCount("/admin/trips"));
  }

  @Test
  void tc102_tripSameFromAndToShouldBlockSubmit() {
    prepareAdminValidationPage();
    fillTripFormWithValidDefaults();
    setElementValue(By.id("adminTo"), "Istanbul");

    submit("#adminTripForm");

    assertFormAndFieldInvalid("adminTripForm", "#adminTo");
    assertEquals(0, recordedPostCount("/admin/trips"));
  }

  @Test
  void tc103_adminRouteSelectsShouldDisableOppositeSelectedCity() {
    prepareAdminValidationPage();

    selectByValue("adminFrom", "Istanbul");
    assertTrue(isDisabled("#adminTo option[value='Istanbul']"));

    selectByValue("adminTo", "Ankara");
    assertTrue(isDisabled("#adminFrom option[value='Ankara']"));
  }

  private static Stream<Arguments> companyRequiredFields() {
    return Stream.of(
        Arguments.of("company name", "#adminCompanyName"),
        Arguments.of("phone", "#adminCompanyPhone"),
        Arguments.of("email", "#adminCompanyEmail"));
  }

  private static Stream<Arguments> tripRequiredFields() {
    return Stream.of(
        Arguments.of("from", "#adminFrom"),
        Arguments.of("to", "#adminTo"),
        Arguments.of("date", "#adminDate"),
        Arguments.of("time", "#adminTime"),
        Arguments.of("price", "#adminPrice"),
        Arguments.of("company", "#adminCompany"));
  }

  private void prepareAdminValidationPage() {
    openPage("index.html");
    setLocalStorage(
        "busgo_user",
        "{\"username\":\"Admin User\",\"email\":\"admin@busgo.com\",\"role\":\"admin\",\"demoBalance\":5000}");
    removeLocalStorage("busgo_token");
    openPage("admin.html");

    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("adminTripForm")));
    wait.until((ignored) -> driver.findElements(By.cssSelector("#adminFrom option")).size() > 1);
    wait.until((ignored) -> driver.findElements(By.cssSelector("#adminCompany option")).size() > 1);
    clearToasts();
    installAdminFetchRecorder();
  }

  private void fillCityForm(String name, String countryCode) {
    setInputValue("#adminCityName", name);
    setInputValue("#adminCityCountry", countryCode);
  }

  private void fillCompanyForm(String name, String phone, String email, String logoUrl) {
    setInputValue("#adminCompanyName", name);
    setInputValue("#adminCompanyPhone", phone);
    setInputValue("#adminCompanyEmail", email);
    setInputValue("#adminCompanyLogo", logoUrl);
  }

  private void fillTripFormWithValidDefaults() {
    selectByValue("adminFrom", "Istanbul");
    selectByValue("adminTo", "Ankara");
    setElementValue(By.id("adminDate"), "2030-05-19");
    setElementValue(By.id("adminTime"), "10:30");
    setElementValue(By.id("adminPrice"), "450");
    selectByValue("adminCompany", "Admin Bus");
  }

  private void submit(String formSelector) {
    driver.findElement(By.cssSelector(formSelector + " button[type='submit']")).click();
  }

  private void selectByValue(String selectId, String value) {
    new Select(driver.findElement(By.id(selectId))).selectByValue(value);
  }

  private void clearField(String selector) {
    WebElement field = driver.findElement(By.cssSelector(selector));
    if ("select".equalsIgnoreCase(field.getTagName())) {
      new Select(field).selectByValue("");
      return;
    }
    setElementValue(By.cssSelector(selector), "");
  }

  private void setElementValue(By locator, String value) {
    WebElement element = driver.findElement(locator);
    ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", element, value);
  }

  private boolean isDisabled(String selector) {
    return (Boolean)
        ((JavascriptExecutor) driver)
            .executeScript("return document.querySelector(arguments[0])?.disabled === true;", selector);
  }

  private void clearToasts() {
    ((JavascriptExecutor) driver).executeScript(
        "const root = document.getElementById('toastContainer'); if (root) root.replaceChildren();");
  }

  private void installAdminFetchRecorder() {
    ((JavascriptExecutor) driver).executeScript(
        """
        window.__adminRequests = [];
        window.fetch = async (url, options = {}) => {
          const request = {
            url: String(url),
            method: String(options.method || "GET").toUpperCase(),
            body: String(options.body || "")
          };
          window.__adminRequests.push(request);

          return {
            ok: true,
            status: request.method === "POST" ? 201 : 200,
            async text() { return "[]"; }
          };
        };
        """);
  }

  private long recordedPostCount(String pathContains) {
    Number count =
        (Number)
            ((JavascriptExecutor) driver)
                .executeScript(
                    """
                    return (window.__adminRequests || [])
                      .filter((request) => request.method === "POST" && request.url.includes(arguments[0]))
                      .length;
                    """,
                    pathContains);
    return count.longValue();
  }

}
