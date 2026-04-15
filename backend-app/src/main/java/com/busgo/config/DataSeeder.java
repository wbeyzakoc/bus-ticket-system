package com.busgo.config;

import com.busgo.model.Role;
import com.busgo.model.User;
import com.busgo.repo.UserRepository;
import java.time.Instant;
import java.util.Locale;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;

  public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public void run(String... args) {
    ensureUser("user@busgo.com", "BusGo User", "1234", Role.USER);
    ensureUser("admin@busgo.com", "BusGo Admin", "1234", Role.ADMIN);
  }

  private void ensureUser(String email, String username, String password, Role role) {
    String normalized = email.toLowerCase(Locale.US);
    if (userRepository.findByEmailIgnoreCase(normalized).isPresent()) return;
    User user = new User();
    user.setEmail(normalized);
    user.setUsername(username);
    user.setPasswordHash(passwordEncoder.encode(password));
    user.setRole(role);
    user.setCreatedAt(Instant.now());
    userRepository.save(user);
  }
}
