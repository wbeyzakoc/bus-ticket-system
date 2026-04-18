package com.busgo.service;

import com.busgo.dto.AdminUserDtos.CreateAdminRequest;
import com.busgo.dto.AdminUserDtos.CreateAdminResponse;
import com.busgo.dto.AuthDtos.AuthResponse;
import com.busgo.dto.AuthDtos.ForgotPasswordRequest;
import com.busgo.dto.AuthDtos.ForgotPasswordResponse;
import com.busgo.dto.AuthDtos.LoginRequest;
import com.busgo.dto.AuthDtos.RegisterRequest;
import com.busgo.dto.UserDto;
import com.busgo.model.AuthToken;
import com.busgo.model.Role;
import com.busgo.model.User;
import com.busgo.repo.AuthTokenRepository;
import com.busgo.repo.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final BigDecimal FALLBACK_DEMO_BALANCE = BigDecimal.valueOf(5000);

  private final UserRepository userRepository;
  private final AuthTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final BookingMailService bookingMailService;
  private final BigDecimal initialDemoBalance;

  public AuthService(
      UserRepository userRepository,
      AuthTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      BookingMailService bookingMailService,
      @Value("${busgo.demo-balance.initial:5000}") BigDecimal initialDemoBalance) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.bookingMailService = bookingMailService;
    this.initialDemoBalance = initialDemoBalance == null ? FALLBACK_DEMO_BALANCE : initialDemoBalance;
  }

  public AuthResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
    String password = normalizeRequiredValue(request.password(), "Password is required");
    validatePassword(password);
    User user = new User();
    user.setUsername(request.username().trim());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.USER);
    user.setDemoBalance(this.initialDemoBalance);
    user.setCreatedAt(Instant.now());
    userRepository.save(user);
    return issueToken(user);
  }

  public CreateAdminResponse createAdmin(User currentAdmin, CreateAdminRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }

    String firstName = normalizeRequiredValue(request.firstName(), "First name is required");
    String lastName = normalizeRequiredValue(request.lastName(), "Last name is required");
    String requestedCompany = normalizeRequiredValue(request.companyName(), "Company name is required");
    String creatorCompany = normalizeOptionalValue(currentAdmin == null ? null : currentAdmin.getCompanyName());
    String companyName = creatorCompany == null ? requestedCompany : creatorCompany;
    String password = normalizeRequiredValue(request.password(), "Password is required");
    validatePassword(password);

    User user = new User();
    user.setUsername(firstName + " " + lastName);
    user.setFirstName(firstName);
    user.setLastName(lastName);
    user.setCompanyName(companyName);
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(Role.ADMIN);
    user.setDemoBalance(this.initialDemoBalance);
    user.setCreatedAt(Instant.now());
    userRepository.save(user);

    return new CreateAdminResponse(user.getEmail(), user.getUsername(), companyName, "admin");
  }

  public AuthResponse login(LoginRequest request) {
    String email = normalizeEmail(request.email());
    User user = userRepository
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    Role expected = parseRole(request.role());
    if (expected != null && expected != user.getRole()) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid account role for this login");
    }

    return issueToken(user);
  }

  @Transactional
  public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {
    String email = normalizeEmail(request.email());
    User user = userRepository
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

    Role expected = parseRole(request.role());
    if (expected != null && expected != user.getRole()) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "No matching account found for this reset request");
    }

    String temporaryPassword = generateTemporaryPassword();
    user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
    userRepository.save(user);
    tokenRepository.deleteByUser(user);

    try {
      bookingMailService.sendPasswordResetMail(user.getEmail(), user.getUsername(), temporaryPassword);
    } catch (IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    return new ForgotPasswordResponse(
        user.getEmail(),
        user.getRole().name().toLowerCase(Locale.US),
        "Temporary password sent to your email.");
  }

  public User requireUser(HttpServletRequest request) {
    String token = extractToken(request);
    if (token == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing auth token");
    }
    return tokenRepository
        .findByToken(token)
        .map(AuthToken::getUser)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid auth token"));
  }

  public User requireAdmin(HttpServletRequest request) {
    User user = requireUser(request);
    if (user.getRole() != Role.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
    }
    return user;
  }

  @Transactional
  public void logout(HttpServletRequest request) {
    String token = extractToken(request);
    if (token != null) {
      tokenRepository.deleteByToken(token);
    }
  }

  public UserDto toDto(User user) {
    return new UserDto(
        user.getEmail(),
        user.getUsername(),
        user.getRole().name().toLowerCase(Locale.US),
        currentDemoBalance(user),
        user.getCompanyName());
  }

  private AuthResponse issueToken(User user) {
    if (user.getDemoBalance() == null) {
      user.setDemoBalance(this.initialDemoBalance);
      userRepository.save(user);
    }
    AuthToken token = new AuthToken();
    token.setToken(UUID.randomUUID().toString());
    token.setUser(user);
    token.setCreatedAt(Instant.now());
    tokenRepository.save(token);
    return new AuthResponse(token.getToken(), toDto(user));
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.US);
  }

  private String normalizeRequiredValue(String value, String message) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    return normalized;
  }

  private String normalizeOptionalValue(String value) {
    String normalized = value == null ? "" : value.trim();
    return normalized.isBlank() ? null : normalized;
  }

  private void validatePassword(String password) {
    if (password.length() < 6) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Password must be at least 6 characters");
    }
  }

  private Role parseRole(String role) {
    if (role == null || role.isBlank()) return null;
    return "admin".equalsIgnoreCase(role) ? Role.ADMIN : Role.USER;
  }

  private BigDecimal currentDemoBalance(User user) {
    return user.getDemoBalance() == null ? this.initialDemoBalance : user.getDemoBalance();
  }

  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) return null;
    return header.substring("Bearer ".length()).trim();
  }

  private String generateTemporaryPassword() {
    return "BG-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
