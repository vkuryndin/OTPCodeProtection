package org.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Service
public class TelegramDeliveryService {

    private final String telegramApiUrl;
    private final String botToken;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public TelegramDeliveryService(@Value("${telegram.api-url}") String telegramApiUrl,
                                   @Value("${telegram.bot-token}") String botToken) {
        this.telegramApiUrl = telegramApiUrl;
        this.botToken = botToken;
    }

    public void sendOtpMessage(String chatId,
                               Long userId,
                               String operationId,
                               String code,
                               LocalDateTime expiresAt) {
        if (chatId == null || chatId.trim().isEmpty()) {
            throw new IllegalArgumentException("User telegram chat id is not set");
        }

        String message = "Your OTP code is: " + code
                + "\nOperation ID: " + operationId
                + "\nUser ID: " + userId
                + "\nExpires at: " + expiresAt;

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

        String url = telegramApiUrl
                + "/bot" + botToken
                + "/sendMessage?chat_id=" + chatId.trim()
                + "&text=" + encodedMessage;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to send Telegram message");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to send Telegram message", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send Telegram message", e);
        }
    }
}