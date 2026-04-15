package com.busgo.service;

import com.busgo.dto.AuthDtos.AuthResponse;
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
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
  private static final BigDecimal FALLBACK_DEMO_BALANCE = BigDecimal.valueOf(5000);

  private final UserRepository userRepository;
  private final AuthTokenRepository tokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final BigDecimal initialDemoBalance;

  public AuthService(
      UserRepository userRepository,
      AuthTokenRepository tokenRepository,
      PasswordEncoder passwordEncoder,
      @Value("${busgo.demo-balance.initial:5000}") BigDecimal initialDemoBalance) {
    this.userRepository = userRepository;
    this.tokenRepository = tokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.initialDemoBalance = initialDemoBalance == null ? FALLBACK_DEMO_BALANCE : initialDemoBalance;
  }

  public AuthResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.findByEmailIgnoreCase(email).isPresent()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
    }
    Role role = parseRole(request.role());
    if (role == null) role = Role.USER;
    User user = new User();
    user.setUsername(request.username().trim());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    user.setRole(role);
    user.setDemoBalance(this.initialDemoBalance);
    user.setCreatedAt(Instant.now());
    userRepository.save(user);
    return issueToken(user);
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
        currentDemoBalance(user));
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
}
