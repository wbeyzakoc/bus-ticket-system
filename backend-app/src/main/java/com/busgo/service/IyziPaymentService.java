package com.busgo.service;

import com.busgo.dto.PaymentDtos.IyziBasketItemRequest;
import com.busgo.dto.PaymentDtos.IyziPaymentItemResponse;
import com.busgo.dto.PaymentDtos.IyziPaymentRequest;
import com.busgo.dto.PaymentDtos.IyziPaymentResponse;
import com.busgo.model.Ticket;
import com.busgo.model.User;
import com.busgo.repo.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class IyziPaymentService {
  private static final String DEFAULT_CURRENCY = "TRY";
  private static final String SANDBOX_BASE_URL = "https://sandbox-api.iyzipay.com";
  private static final DateTimeFormatter IYZI_DATE_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Europe/Istanbul"));

  private final ObjectMapper objectMapper;
  private final UserRepository userRepository;
  private final HttpClient httpClient;
  private final String baseUrl;
  private final String apiKey;
  private final String secretKey;
  private final BigDecimal initialDemoBalance;

  public IyziPaymentService(
      ObjectMapper objectMapper,
      UserRepository userRepository,
      @Value("${busgo.iyzico.base-url:" + SANDBOX_BASE_URL + "}") String baseUrl,
      @Value("${busgo.iyzico.api-key:}") String apiKey,
      @Value("${busgo.iyzico.secret-key:}") String secretKey,
      @Value("${busgo.demo-balance.initial:5000}") BigDecimal initialDemoBalance) {
    this.objectMapper = objectMapper;
    this.userRepository = userRepository;
    this.httpClient = HttpClient.newHttpClient();
    this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? SANDBOX_BASE_URL : baseUrl.trim();
    this.apiKey = apiKey == null ? "" : apiKey.trim();
    this.secretKey = secretKey == null ? "" : secretKey.trim();
    this.initialDemoBalance =
        initialDemoBalance == null ? BigDecimal.valueOf(5000) : initialDemoBalance;
  }

  public IyziPaymentResponse authorizePayment(
      User user, IyziPaymentRequest request, String clientIp) {
    requireConfigured();
    validateCardData(request);
    BigDecimal currentBalance = ensureDemoBalance(user);
    if (currentBalance.compareTo(request.amount()) < 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Insufficient demo balance. Top balance must be at least " + request.amount());
    }

    String rawBody = buildPaymentBody(user, request, clientIp);
    JsonNode root = sendRequest("/payment/auth", rawBody);
    if (!"success".equalsIgnoreCase(text(root, "status"))) {
      throw iyziFailure(root);
    }

    String paymentId = firstNonBlank(text(root, "paymentId"), text(root.path("data"), "paymentId"));
    if (paymentId == null || paymentId.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "iyzico payment id is missing");
    }

    List<IyziPaymentItemResponse> items = mapPaymentItems(root, request.items());
    BigDecimal balanceAfter = currentBalance.subtract(request.amount());
    user.setDemoBalance(balanceAfter);
    userRepository.save(user);

    return new IyziPaymentResponse(paymentId, paymentId, "success", balanceAfter, items);
  }

  public IyziRefundResult refundTicket(User user, Ticket ticket, BigDecimal amount) {
    if (ticket.getProviderPaymentTransactionId() == null
        || ticket.getProviderPaymentTransactionId().isBlank()) {
      BigDecimal balanceAfter = creditDemoBalance(user, amount);
      return new IyziRefundResult("LOCAL-REFUND-" + UUID.randomUUID(), balanceAfter, true);
    }

    requireConfigured();
    Map<String, Object> payload = new HashMap<>();
    payload.put("locale", "tr");
    payload.put("conversationId", "refund-" + ticket.getId());
    payload.put("paymentTransactionId", ticket.getProviderPaymentTransactionId());
    payload.put("price", asMoney(amount));
    payload.put("ip", "127.0.0.1");

    String rawBody = writeJson(payload);
    JsonNode root = sendRequest("/payment/refund", rawBody);
    if (!"success".equalsIgnoreCase(text(root, "status"))) {
      throw iyziFailure(root);
    }

    BigDecimal balanceAfter = creditDemoBalance(user, amount);

    String refundRef =
        firstNonBlank(
            text(root, "paymentTransactionId"),
            text(root, "conversationId"),
            "RFND-" + UUID.randomUUID());
    return new IyziRefundResult(refundRef, balanceAfter, false);
  }

  public record IyziRefundResult(String reference, BigDecimal balanceAfter, boolean localFallback) {}

  public BigDecimal creditDemoBalance(User user, BigDecimal amount) {
    BigDecimal balanceAfter = ensureDemoBalance(user).add(amount);
    user.setDemoBalance(balanceAfter);
    userRepository.save(user);
    return balanceAfter;
  }

  private void requireConfigured() {
    if (apiKey.isBlank() || secretKey.isBlank()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "iyzico sandbox keys are not configured. Set IYZI_API_KEY and IYZI_SECRET_KEY.");
    }
  }

  private void validateCardData(IyziPaymentRequest request) {
    String cardNumber = request.cardNumber().replaceAll("\\s+", "");
    if (!cardNumber.matches("^\\d{16}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid card number");
    }
    if (!request.expiry().matches("^\\d{2}/\\d{2}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid expiry");
    }
    if (!request.cvv().matches("^\\d{3,4}$")) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid CVV");
    }
  }

  private BigDecimal ensureDemoBalance(User user) {
    if (user.getDemoBalance() == null) {
      user.setDemoBalance(initialDemoBalance);
      userRepository.save(user);
    }
    return user.getDemoBalance();
  }

  private String buildPaymentBody(User user, IyziPaymentRequest request, String clientIp) {
    String[] expiry = request.expiry().split("/");
    String[] fullName = splitName(request.cardHolderName(), user.getUsername());
    List<Map<String, Object>> basketItems = new ArrayList<>();
    for (IyziBasketItemRequest item : request.items()) {
      Map<String, Object> basketItem = new HashMap<>();
      basketItem.put("id", "seat-" + item.seatNumber());
      basketItem.put("name", "Bus Ticket Seat " + item.seatNumber());
      basketItem.put("category1", "Travel");
      basketItem.put("itemType", "VIRTUAL");
      basketItem.put("price", asMoney(item.amount()));
      basketItems.add(basketItem);
    }

    Map<String, Object> payload = new HashMap<>();
    payload.put("locale", "tr");
    payload.put("conversationId", "busgo-" + UUID.randomUUID());
    payload.put("price", asMoney(request.amount()));
    payload.put("paidPrice", asMoney(request.amount()));
    payload.put("currency", normalizeCurrency(request.currency()));
    payload.put("installment", 1);
    payload.put("paymentGroup", "PRODUCT");
    payload.put("paymentCard", paymentCard(request.cardNumber(), request.cvv(), expiry[0], expiry[1], request.cardHolderName()));
    payload.put("buyer", buyer(user, fullName, clientIp));
    payload.put("shippingAddress", address(request.cardHolderName()));
    payload.put("billingAddress", address(request.cardHolderName()));
    payload.put("basketItems", basketItems);
    return writeJson(payload);
  }

  private Map<String, Object> paymentCard(
      String cardNumber, String cvv, String expireMonth, String expireYear, String cardHolderName) {
    Map<String, Object> paymentCard = new HashMap<>();
    paymentCard.put("cardHolderName", cardHolderName);
    paymentCard.put("cardNumber", cardNumber.replaceAll("\\s+", ""));
    paymentCard.put("expireMonth", expireMonth);
    paymentCard.put("expireYear", "20" + expireYear);
    paymentCard.put("cvc", cvv);
    paymentCard.put("registerCard", 0);
    return paymentCard;
  }

  private Map<String, Object> buyer(User user, String[] nameParts, String clientIp) {
    Map<String, Object> buyer = new HashMap<>();
    buyer.put("id", user.getId() == null ? UUID.randomUUID().toString() : user.getId().toString());
    buyer.put("name", nameParts[0]);
    buyer.put("surname", nameParts[1]);
    buyer.put("gsmNumber", "+905555555555");
    buyer.put("email", user.getEmail());
    buyer.put("identityNumber", "11111111111");
    buyer.put("lastLoginDate", IYZI_DATE_FORMAT.format(Instant.now()));
    buyer.put("registrationDate", IYZI_DATE_FORMAT.format(user.getCreatedAt() == null ? Instant.now() : user.getCreatedAt()));
    buyer.put("registrationAddress", "BusGo Demo Address");
    buyer.put("ip", normalizeIp(clientIp));
    buyer.put("city", "Istanbul");
    buyer.put("country", "Turkey");
    buyer.put("zipCode", "34000");
    return buyer;
  }

  private Map<String, Object> address(String contactName) {
    Map<String, Object> address = new HashMap<>();
    address.put("contactName", contactName);
    address.put("city", "Istanbul");
    address.put("country", "Turkey");
    address.put("address", "BusGo Demo Address");
    address.put("zipCode", "34000");
    return address;
  }

  private List<IyziPaymentItemResponse> mapPaymentItems(
      JsonNode root, List<IyziBasketItemRequest> requestedItems) {
    JsonNode transactionsNode = root.path("itemTransactions");
    if (!transactionsNode.isArray() || transactionsNode.isEmpty()) {
      transactionsNode = root.path("data").path("itemTransactions");
    }
    if (!transactionsNode.isArray() || transactionsNode.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "iyzico payment item transactions are missing");
    }

    Map<String, JsonNode> byItemId = new HashMap<>();
    int index = 0;
    for (JsonNode node : transactionsNode) {
      byItemId.put(text(node, "itemId"), node);
      byItemId.put("index-" + index, node);
      index += 1;
    }

    List<IyziPaymentItemResponse> items = new ArrayList<>();
    for (int i = 0; i < requestedItems.size(); i += 1) {
      IyziBasketItemRequest requested = requestedItems.get(i);
      JsonNode itemNode =
          firstPresent(byItemId.get("seat-" + requested.seatNumber()), byItemId.get("index-" + i));
      if (itemNode == null) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "iyzico item transaction missing for seat " + requested.seatNumber());
      }
      String paymentTransactionId =
          firstNonBlank(text(itemNode, "paymentTransactionId"), text(itemNode.path("data"), "paymentTransactionId"));
      if (paymentTransactionId == null || paymentTransactionId.isBlank()) {
        throw new ResponseStatusException(
            HttpStatus.BAD_GATEWAY,
            "iyzico payment transaction id missing for seat " + requested.seatNumber());
      }
      items.add(
          new IyziPaymentItemResponse(
              requested.seatNumber(), paymentTransactionId, requested.amount()));
    }
    return items;
  }

  private JsonNode sendRequest(String path, String rawBody) {
    try {
      String randomKey = String.valueOf(System.currentTimeMillis());
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(baseUrl + path))
              .header("Content-Type", "application/json")
              .header("Authorization", buildAuthorization(path, rawBody, randomKey))
              .header("x-iyzi-rnd", randomKey)
              .POST(HttpRequest.BodyPublishers.ofString(rawBody))
              .build();

      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      JsonNode root = objectMapper.readTree(response.body());
      if (response.statusCode() >= 400) {
        throw iyziFailure(root);
      }
      return root;
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "iyzico response could not be read", ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "iyzico request interrupted", ex);
    }
  }

  private String buildAuthorization(String uriPath, String rawBody, String randomKey) {
    try {
      String payload = randomKey + uriPath + rawBody;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String signature =
          HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
      String authorizationString =
          "apiKey:" + apiKey + "&randomKey:" + randomKey + "&signature:" + signature;
      String encoded =
          Base64.getEncoder().encodeToString(authorizationString.getBytes(StandardCharsets.UTF_8));
      return "IYZWSv2 " + encoded;
    } catch (GeneralSecurityException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "iyzico authorization could not be created", ex);
    }
  }

  private ResponseStatusException iyziFailure(JsonNode root) {
    String message =
        firstNonBlank(
            text(root, "errorMessage"),
            text(root, "errorCode"),
            text(root, "status"),
            "iyzico payment failed");
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }

  private String normalizeCurrency(String currency) {
    if (currency == null || currency.isBlank()) return DEFAULT_CURRENCY;
    return currency.trim().toUpperCase(Locale.US);
  }

  private String normalizeIp(String clientIp) {
    if (clientIp == null || clientIp.isBlank()) return "127.0.0.1";
    return clientIp.contains(":") ? "127.0.0.1" : clientIp;
  }

  private String[] splitName(String preferred, String fallback) {
    String source =
        preferred != null && !preferred.isBlank() ? preferred.trim() : fallback == null ? "BusGo User" : fallback.trim();
    String[] parts = source.split("\\s+", 2);
    if (parts.length == 1) {
      return new String[] {parts[0], "User"};
    }
    return new String[] {parts[0], parts[1]};
  }

  private String asMoney(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  private String writeJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (IOException ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "iyzico request could not be created", ex);
    }
  }

  private String text(JsonNode node, String field) {
    if (node == null || node.isMissingNode() || node.isNull()) return null;
    JsonNode child = node.path(field);
    if (child.isMissingNode() || child.isNull()) return null;
    String value = child.asText();
    return value == null || value.isBlank() ? null : value;
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) return value;
    }
    return null;
  }

  private JsonNode firstPresent(JsonNode... values) {
    for (JsonNode value : values) {
      if (value != null && !value.isMissingNode() && !value.isNull()) return value;
    }
    return null;
  }
}
