package org.example.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Properties;

@Service
public class EmailDeliveryService {

    private final Session session;
    private final String fromEmail;

    public EmailDeliveryService(
            @Value("${email.username}") String username,
            @Value("${email.password}") String password,
            @Value("${email.from}") String fromEmail,
            @Value("${mail.smtp.host}") String host,
            @Value("${mail.smtp.port}") String port,
            @Value("${mail.smtp.auth}") String auth,
            @Value("${mail.smtp.starttls.enable}") String startTls
    ) {
        this.fromEmail = fromEmail;

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", port);
        props.put("mail.smtp.auth", auth);
        props.put("mail.smtp.starttls.enable", startTls);

        this.session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });
    }

    public void sendOtpEmail(String toEmail,
                             Long userId,
                             String operationId,
                             String code,
                             LocalDateTime expiresAt) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("Delivery target is required");
        }

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail.trim()));
            message.setSubject("Your OTP Code");
            message.setText(
                    "Your OTP code is: " + code + System.lineSeparator()
                            + "Operation ID: " + operationId + System.lineSeparator()
                            + "User ID: " + userId + System.lineSeparator()
                            + "Expires at: " + expiresAt
            );

            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
    public void sendTelegramBindEmail(String toEmail, String bindLink, LocalDateTime expiresAt) {
        if (toEmail == null || toEmail.trim().isEmpty()) {
            throw new IllegalArgumentException("User email is not set");
        }

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail.trim()));
            message.setSubject("Bind your Telegram account");
            message.setText(
                    "To bind your Telegram account, open this link:\n"
                            + bindLink + "\n\n"
                            + "This link expires at: " + expiresAt
            );

            Transport.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}