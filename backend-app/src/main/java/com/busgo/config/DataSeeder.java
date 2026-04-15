package com.busgo.config;

import com.busgo.model.Role;
import com.busgo.model.User;
import com.busgo.repo.UserRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final BigDecimal initialDemoBalance;

  public DataSeeder(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${busgo.demo-balance.initial:5000}") BigDecimal initialDemoBalance) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.initialDemoBalance = initialDemoBalance == null ? BigDecimal.valueOf(5000) : initialDemoBalance;
  }

  @Override
  public void run(String... args) {
    ensureUser("user@busgo.com", "BusGo User", "1234", Role.USER);
    ensureUser("admin@busgo.com", "BusGo Admin", "1234", Role.ADMIN);
  }

  private void ensureUser(String email, String username, String password, Role role) {
    String normalized = email.toLowerCase(Locale.US);
    User existing = userRepository.findByEmailIgnoreCase(normalized).orElse(null);
    if (existing != null) {
      if (existing.getDemoBalance() == null) {
        existing.setDemoBalance(initialDemoBalance);
        userRepository.save(existing);
      }
      return;
    }
    User user = new User();
    user.setEmail(normalized);
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setDemoBalance(initialDemoBalance);
    user.setCreatedAt(Instant.now());
    userRepository.save(user);
  }
}
