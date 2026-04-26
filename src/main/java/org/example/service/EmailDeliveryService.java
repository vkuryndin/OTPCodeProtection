package org.example.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

  private final Session session;
  private final String fromEmail;

  public EmailDeliveryService(
      @Value("${email.username}") String username,
      @Value("${email.password}") String password,
      @Value("${email.from}") String fromEmail,
      @Value("${mail.smtp.host}") String host,
      @Value("${mail.smtp.port}") String port,
      @Value("${mail.smtp.auth}") String auth,
      @Value("${mail.smtp.starttls.enable}") String startTls) {
    this.fromEmail = fromEmail;
    this.session = createSession(username, password, host, port, auth, startTls);
  }

  public void sendOtpEmail(
      String toEmail, Long userId, String operationId, String code, LocalDateTime expiresAt) {
    if (toEmail == null || toEmail.trim().isEmpty()) {
      throw new IllegalArgumentException("Delivery target is required");
    }

    String normalizedEmail = toEmail.trim();
    String body =
        "Your OTP code is: "
            + code
            + System.lineSeparator()
            + "Operation ID: "
            + operationId
            + System.lineSeparator()
            + "User ID: "
            + userId
            + System.lineSeparator()
            + "Expires at: "
            + expiresAt;

    try {
      Message message = buildMessage(normalizedEmail, "Your OTP Code", body);
      Transport.send(message);

      log.info(
          "OTP email sent: userId={}, operationId={}, to={}", userId, operationId, normalizedEmail);
    } catch (Exception e) {
      log.error(
          "Email service is unavailable: userId={}, operationId={}, to={}",
          userId,
          operationId,
          normalizedEmail,
          e);
      throw new RuntimeException("Email service is unavailable. Try again later.");
    }
  }

  public void sendTelegramBindEmail(String toEmail, String bindLink, LocalDateTime expiresAt) {
    if (toEmail == null || toEmail.trim().isEmpty()) {
      throw new IllegalArgumentException("User email is not set");
    }

    String normalizedEmail = toEmail.trim();
    String body =
        "To bind your Telegram account, open this link:\n"
            + bindLink
            + "\n\n"
            + "This link expires at: "
            + expiresAt;

    try {
      Message message = buildMessage(normalizedEmail, "Bind your Telegram account", body);
      Transport.send(message);

      log.info("Telegram bind email sent: to={}, expiresAt={}", normalizedEmail, expiresAt);
    } catch (Exception e) {
      log.error(
          "Email service is unavailable while sending Telegram bind email: to={}",
          normalizedEmail,
          e);
      throw new RuntimeException("Email service is unavailable. Try again later.");
    }
  }

  private Session createSession(
      String username, String password, String host, String port, String auth, String startTls) {
    Properties props = new Properties();
    props.put("mail.smtp.host", host);
    props.put("mail.smtp.port", port);
    props.put("mail.smtp.auth", auth);
    props.put("mail.smtp.starttls.enable", startTls);

    return Session.getInstance(
        props,
        new Authenticator() {
          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(username, password);
          }
        });
  }

  private Message buildMessage(String toEmail, String subject, String body) throws Exception {
    Message message = new MimeMessage(session);
    message.setFrom(new InternetAddress(fromEmail));
    message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
    message.setSubject(subject);
    message.setText(body);
    return message;
  }
}
