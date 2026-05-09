package com.busgo.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.opentest4j.TestAbortedException;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

@Tag("selenium")
abstract class BaseFrontendSeleniumTest {

  protected WebDriver driver;
  protected WebDriverWait wait;
  private Path frontendRoot;

  @BeforeEach
  void setUp() {
    Path currentDir = Paths.get("").toAbsolutePath();
    frontendRoot = currentDir.getFileName() != null && "backend-app".equals(currentDir.getFileName().toString())
        ? currentDir.getParent()
        : currentDir;

    ChromeOptions options = new ChromeOptions();
    options.addArguments(
        "--headless=new",
        "--window-size=1440,1200",
        "--allow-file-access-from-files",
        "--disable-gpu",
        "--no-sandbox");

    try {
      driver = new ChromeDriver(options);
    } catch (WebDriverException error) {
      throw new TestAbortedException("Chrome/WebDriver is not available for Selenium tests.", error);
    }

    wait = new WebDriverWait(driver, Duration.ofSeconds(5));
  }

  @AfterEach
  void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  protected void openPage(String pageName) {
    driver.get(frontendRoot.resolve(pageName).toUri().toString());
  }

  protected void setLocalStorage(String key, String jsonValue) {
    ((JavascriptExecutor) driver).executeScript("localStorage.setItem(arguments[0], arguments[1]);", key, jsonValue);
  }

  protected void removeLocalStorage(String key) {
    ((JavascriptExecutor) driver).executeScript("localStorage.removeItem(arguments[0]);", key);
  }

  protected void stubFetchJson(String urlContains, int status, String bodyJson) {
    ((JavascriptExecutor) driver).executeScript(
        """
        const matcher = arguments[0];
        const status = arguments[1];
        const body = arguments[2];
        window.fetch = async (url, options = {}) => {
          if (String(url).includes(matcher)) {
            return {
              ok: status >= 200 && status < 300,
              status,
              async text() { return body; }
            };
          }
          return {
            ok: false,
            status: 404,
            async text() { return JSON.stringify({ message: "Not mocked" }); }
          };
        };
        """,
        urlContains, status, bodyJson);
  }

  protected void stubFetchRoutes(String routesJson) {
    ((JavascriptExecutor) driver).executeScript(
        """
        const routes = JSON.parse(arguments[0]);
        window.fetch = async (url, options = {}) => {
          const key = Object.keys(routes).find((candidate) => String(url).includes(candidate));
          if (key) {
            const route = routes[key];
            return {
              ok: route.status >= 200 && route.status < 300,
              status: route.status,
              async text() { return route.body; }
            };
          }
          return {
            ok: false,
            status: 404,
            async text() { return JSON.stringify({ message: "Not mocked" }); }
          };
        };
        """,
        routesJson);
  }

  protected void prepareLoggedInUserPage(String pageName) {
    openPage(pageName);
    setLocalStorage("busgo_user", "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    removeLocalStorage("busgo_token");
    openPage(pageName);
  }

  protected void prepareAdminUserPage(String pageName) {
    openPage(pageName);
    setLocalStorage("busgo_user", "{\"username\":\"Admin User\",\"email\":\"admin@busgo.com\",\"role\":\"admin\",\"demoBalance\":5000}");
    removeLocalStorage("busgo_token");
    openPage(pageName);
  }

  protected void setInputValue(String cssSelector, String value) {
    WebElement input = driver.findElement(By.cssSelector(cssSelector));
    input.clear();
    input.sendKeys(value);
  }

  protected void selectCity(String selectId, String city) {
    wait.until(ExpectedConditions.presenceOfElementLocated(By.id(selectId)));
    new Select(driver.findElement(By.id(selectId))).selectByValue(city);
  }

  protected void setDateValue(String inputId, String value) {
    WebElement input = driver.findElement(By.id(inputId));
    ((JavascriptExecutor) driver).executeScript("arguments[0].value = arguments[1];", input, value);
  }

  protected boolean checkValidity(WebElement element) {
    return (Boolean) ((JavascriptExecutor) driver).executeScript("return arguments[0].checkValidity();", element);
  }

  protected boolean isInvalid(WebElement element) {
    return (Boolean) ((JavascriptExecutor) driver).executeScript("return arguments[0].matches(':invalid');", element);
  }

  protected String waitForToastText() {
    WebElement toast = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("#toastContainer .toast")));
    return toast.getText();
  }

  protected boolean pageSourceContains(String text) {
    return wait.until((ignored) -> driver.getPageSource().contains(text));
  }

  protected String summaryText() {
    return driver.findElement(By.id("summaryBox")).getText();
  }

  protected void assertFormAndFieldInvalid(String formId, String fieldCssSelector) {
    WebElement form = driver.findElement(By.id(formId));
    WebElement field = driver.findElement(By.cssSelector(fieldCssSelector));
    assertFalse(checkValidity(form));
    assertFalse(checkValidity(field));
    assertTrue(isInvalid(field));
  }

  protected void preparePassengerPage() {
    openPage("passenger.html");
    ((JavascriptExecutor) driver).executeScript(
        "localStorage.setItem('busgo_search', arguments[0]);"
            + "localStorage.setItem('busgo_trip', arguments[1]);",
        "{\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\",\"ticketCount\":1}",
        "{\"id\":\"trip-1\",\"company\":\"BusGo Express\",\"departureTime\":\"09:30\",\"basePrice\":450}");
    openPage("passenger.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".passenger-input-card")));
  }

  protected void fillPassengerWithValidDefaults() {
    setInputValue(".first-name", "Ali");
    setInputValue(".last-name", "Yilmaz");
    setInputValue(".tc-no", "12345678901");
    setInputValue(".age", "25");
    setInputValue(".email", "ali@example.com");
    setInputValue(".phone", "+905551112233");
  }

  protected void prepareSeatsPage() {
    openPage("seats.html");
    ((JavascriptExecutor) driver).executeScript(
        "localStorage.setItem('busgo_search', arguments[0]);"
            + "localStorage.setItem('busgo_trip', arguments[1]);"
            + "localStorage.setItem('busgo_passengers_draft', arguments[2]);"
            + "localStorage.setItem('busgo_user', arguments[3]);",
        "{\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\",\"ticketCount\":1}",
        "{\"id\":\"trip-1\",\"company\":\"BusGo Express\",\"departureTime\":\"09:30\",\"duration\":\"6h 15m\",\"basePrice\":450,"
            + "\"departureDateTime\":\"2030-01-15T09:30:00\"}",
        "[{\"firstName\":\"Ali\",\"lastName\":\"Yilmaz\",\"name\":\"Ali Yilmaz\",\"tc\":\"12345678901\","
            + "\"age\":25,\"email\":\"ali@example.com\",\"phone\":\"+905551112233\",\"gender\":\"male\","
            + "\"baggage\":15,\"seatNumber\":null}]",
        "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    openPage("seats.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("button[data-seat='1']")));
  }

  protected void preparePaymentPage() {
    openPage("payment.html");
    ((JavascriptExecutor) driver).executeScript(
        "localStorage.setItem('busgo_booking', arguments[0]);"
            + "localStorage.setItem('busgo_user', arguments[1]);",
        "{\"search\":{\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\"},"
            + "\"trip\":{\"company\":\"BusGo Express\",\"departureTime\":\"09:30\",\"basePrice\":450},"
            + "\"passengers\":[{\"name\":\"Ali Yilmaz\",\"seatNumber\":5,\"gender\":\"male\",\"age\":25,\"baggage\":15}],"
            + "\"total\":450}",
        "{\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    openPage("payment.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("paymentForm")));
  }

  protected void prepareTripsPage(String tripsJson, String nearestJson) {
    openPage("trips.html");
    setLocalStorage("busgo_search", "{\"from\":\"Ankara\",\"to\":\"Izmir\",\"date\":\"2030-01-15\",\"ticketCount\":1}");
    String routesJson = "{\"/trips?\":{\"status\":200,\"body\":" + quoteJson(tripsJson) + "},"
        + "\"/trips/nearest?\":{\"status\":200,\"body\":" + quoteJson(nearestJson) + "}}";
    stubFetchRoutes(routesJson);
    openPage("trips.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("tripList")));
  }

  protected void prepareProfilePageWithTickets(String ticketsJson) {
    openPage("profile.html");
    setLocalStorage("busgo_user", "{\"username\":\"Ali Yilmaz\",\"email\":\"ali@example.com\",\"role\":\"user\",\"demoBalance\":5000}");
    String routesJson = "{\"/tickets/me\":{\"status\":200,\"body\":" + quoteJson(ticketsJson) + "}}";
    stubFetchRoutes(routesJson);
    openPage("profile.html");
    wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("myTickets")));
  }

  private String quoteJson(String raw) {
    return "\"" + raw.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }
}
