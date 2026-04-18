package com.busgo.service;

import com.busgo.service.BookingConfirmationMailEvent.RecipientBookingMail;
import com.busgo.service.BookingConfirmationMailEvent.TicketMailLine;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class BookingMailService {
  private static final Logger log = LoggerFactory.getLogger(BookingMailService.class);
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final String configuredFrom;
  private final String fallbackFrom;
  private final String subjectPrefix;

  public BookingMailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${busgo.mail.from:}") String configuredFrom,
      @Value("${spring.mail.username:}") String fallbackFrom,
      @Value("${busgo.mail.booking-subject-prefix:[BusGo]}") String subjectPrefix) {
    this.mailSenderProvider = mailSenderProvider;
    this.configuredFrom = configuredFrom == null ? "" : configuredFrom.trim();
    this.fallbackFrom = fallbackFrom == null ? "" : fallbackFrom.trim();
    this.subjectPrefix = subjectPrefix == null ? "[BusGo]" : subjectPrefix.trim();
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void handleBookingCompleted(BookingConfirmationMailEvent event) {
    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender == null) {
      log.info("Booking confirmation email skipped because JavaMailSender is not configured.");
      return;
    }

    String from = resolveFromAddress();
    if (from.isBlank()) {
      log.info("Booking confirmation email skipped because sender address is not configured.");
      return;
    }

    for (RecipientBookingMail recipient : event.recipients()) {
      if (recipient == null || recipient.email() == null || recipient.email().isBlank()) continue;
      try {
        mailSender.send(buildMessage(from, recipient, event));
      } catch (MailException ex) {
        log.warn(
            "Booking confirmation email could not be sent to {} for reference {}",
            recipient.email(),
            event.bookingReference(),
            ex);
      }
    }
  }

  public void sendPasswordResetMail(String email, String userName, String temporaryPassword) {
    String recipientEmail = email == null ? "" : email.trim();
    if (recipientEmail.isBlank()) {
      throw new IllegalStateException("Password reset email could not be sent because recipient email is missing.");
    }

    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender == null) {
      throw new IllegalStateException("Mail service is not configured.");
    }

    String from = resolveFromAddress();
    if (from.isBlank()) {
      throw new IllegalStateException("Mail sender address is not configured.");
    }

    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipientEmail);
    message.setSubject(subjectPrefix + " Sifre sifirlama");
    message.setText(buildPasswordResetBody(userName, recipientEmail, temporaryPassword));

    try {
      mailSender.send(message);
    } catch (MailException ex) {
      log.warn("Password reset email could not be sent to {}", recipientEmail, ex);
      throw new IllegalStateException("Password reset email could not be sent right now.");
    }
  }

  private SimpleMailMessage buildMessage(
      String from, RecipientBookingMail recipient, BookingConfirmationMailEvent event) {
    SimpleMailMessage message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(recipient.email());
    message.setSubject(subjectPrefix + " Bilet alma isleminiz basarili");
    message.setText(buildBody(recipient, event));
    return message;
  }

  private String buildBody(RecipientBookingMail recipient, BookingConfirmationMailEvent event) {
    StringBuilder body = new StringBuilder();
    body.append("Merhaba ").append(nullToFallback(recipient.userName(), "Yolcu")).append(",\n\n");
    body.append("Bilet alma isleminiz basariyla tamamlandi.\n\n");
    body.append("Guzergah: ").append(event.route()).append('\n');
    body.append("Sefer Tarihi: ").append(formatDateTime(event.departureDateTime())).append('\n');
    body.append("Firma: ").append(event.company()).append('\n');
    body.append("Rezervasyon Ref: ").append(event.bookingReference()).append("\n\n");
    body.append("Biletleriniz:\n");
    appendTickets(body, recipient.tickets());
    body.append('\n');
    body.append("Toplam: ").append(formatMoney(recipient.total())).append("\n\n");
    body.append("Iyi yolculuklar,\nBusGo");
    return body.toString();
  }

  private String buildPasswordResetBody(
      String userName, String recipientEmail, String temporaryPassword) {
    String displayName = nullToFallback(userName, recipientEmail);

    StringBuilder body = new StringBuilder();
    body.append("Merhaba ").append(displayName).append(",\n\n");
    body.append("BusGo hesabiniza ait gecici sifreniz olusturuldu.\n\n");
    body.append("Gecici sifre: ").append(nullToFallback(temporaryPassword, "-")).append('\n');
    body.append("Giris yaptiktan sonra sifrenizi degistirmenizi oneririz.\n\n");
    body.append("BusGo");
    return body.toString();
  }

  private void appendTickets(StringBuilder body, List<TicketMailLine> tickets) {
    if (tickets == null || tickets.isEmpty()) {
      body.append("- Bilet detayi bulunamadi.\n");
      return;
    }
    for (TicketMailLine ticket : tickets) {
      if (ticket == null) continue;
      body.append("- ")
          .append(nullToFallback(ticket.passengerName(), "Passenger"))
          .append(" | Koltuk ")
          .append(nullToFallback(ticket.seatNumber(), "-"))
          .append(" | ")
          .append(formatMoney(ticket.price()))
          .append('\n');
    }
  }

  private String resolveFromAddress() {
    if (!configuredFrom.isBlank()) return configuredFrom;
    return fallbackFrom;
  }

  private String formatDateTime(String rawDateTime) {
    if (rawDateTime == null || rawDateTime.isBlank()) return "-";
    try {
      return LocalDateTime.parse(rawDateTime).format(DATE_TIME_FORMAT);
    } catch (RuntimeException ex) {
      return rawDateTime;
    }
  }

  private String formatMoney(BigDecimal amount) {
    if (amount == null) return "0.00 TRY";
    return amount.stripTrailingZeros().toPlainString() + " TRY";
  }

  private String nullToFallback(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }
}
