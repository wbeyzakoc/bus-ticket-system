package com.busgo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.busgo.dto.AdminUserDtos.CreateAdminRequest;
import com.busgo.dto.AdminUserDtos.CreateAdminResponse;
import com.busgo.dto.AuthDtos.ForgotPasswordRequest;
import com.busgo.dto.AuthDtos.ForgotPasswordResponse;
import com.busgo.model.Role;
import com.busgo.model.User;
import com.busgo.repo.AuthTokenRepository;
import com.busgo.repo.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
  @Mock private UserRepository userRepository;
  @Mock private AuthTokenRepository tokenRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
  @Mock private JavaMailSender javaMailSender;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    BookingMailService bookingMailService =
        new BookingMailService(mailSenderProvider, "noreply@busgo.local", "", "[BusGo]");
    authService =
        new AuthService(
            userRepository,
            tokenRepository,
            passwordEncoder,
            bookingMailService,
            BigDecimal.valueOf(5000));
  }

  @Test
  void forgotPasswordSendsTemporaryPasswordByEmail() {
    User user = new User();
    user.setEmail("user@busgo.local");
    user.setUsername("Busgo User");
    user.setRole(Role.USER);
    user.setPasswordHash("old-hash");

    when(userRepository.findByEmailIgnoreCase("user@busgo.local")).thenReturn(Optional.of(user));
    when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
    when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

    ForgotPasswordResponse response =
        authService.forgotPassword(new ForgotPasswordRequest("user@busgo.local", "user"));

    ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
    verify(javaMailSender).send(mailCaptor.capture());
    verify(tokenRepository).deleteByUser(user);

    SimpleMailMessage sentMail = mailCaptor.getValue();
    String[] recipients = sentMail.getTo();
    String body = sentMail.getText();
    assertTrue(recipients != null && recipients.length == 1);
    assertEquals("user@busgo.local", recipients[0]);
    assertEquals("[BusGo] Sifre sifirlama", sentMail.getSubject());
    assertTrue(body != null && body.contains("Gecici sifre: BG-"));

    int prefixIndex = body.indexOf("Gecici sifre: BG-");
    String temporaryPassword = body.substring(prefixIndex + "Gecici sifre: ".length(), prefixIndex + "Gecici sifre: ".length() + 11);
    assertTrue(temporaryPassword.startsWith("BG-"));
    assertEquals(11, temporaryPassword.length());
    assertEquals("encoded-password", user.getPasswordHash());
    assertEquals("user@busgo.local", response.email());
    assertEquals("user", response.role());
    assertEquals("Temporary password sent to your email.", response.message());
  }

  @Test
  void forgotPasswordReturnsServiceUnavailableWhenMailSendFails() {
    User user = new User();
    user.setEmail("admin@busgo.local");
    user.setUsername("Admin User");
    user.setRole(Role.ADMIN);

    when(userRepository.findByEmailIgnoreCase("admin@busgo.local")).thenReturn(Optional.of(user));
    when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");
    when(mailSenderProvider.getIfAvailable()).thenReturn(null);

    ResponseStatusException error =
        assertThrows(
            ResponseStatusException.class,
            () -> authService.forgotPassword(new ForgotPasswordRequest("admin@busgo.local", "admin")));

    assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    assertEquals("Mail service is not configured.", error.getReason());
  }

  @Test
  void createAdminUsesCurrentAdminsCompanyWhenPresent() {
    User currentAdmin = new User();
    currentAdmin.setRole(Role.ADMIN);
    currentAdmin.setCompanyName("Scoped Lines");

    when(userRepository.findByEmailIgnoreCase("newadmin@busgo.local")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("secret12")).thenReturn("encoded-password");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    CreateAdminResponse response =
        authService.createAdmin(
            currentAdmin,
            new CreateAdminRequest(
                "Ayse", "Yilmaz", "Different Company", "newadmin@busgo.local", "secret12"));

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());

    assertEquals("Scoped Lines", userCaptor.getValue().getCompanyName());
    assertEquals("Scoped Lines", response.companyName());
    assertEquals("admin", response.role());
  }
}
