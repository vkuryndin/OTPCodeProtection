package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Locale;

@Service
public class TelegramDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(TelegramDeliveryService.class);

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

        String normalizedChatId = chatId.trim();
        String url = buildSendMessageUrl(normalizedChatId, userId, operationId, code, expiresAt);

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
                String message = resolveTelegramErrorMessage(response.statusCode(), response.body());

                log.error(
                        "Failed to send Telegram message: userId={}, operationId={}, chatId={}, statusCode={}, responseBody={}",
                        userId,
                        operationId,
                        normalizedChatId,
                        response.statusCode(),
                        response.body()
                );

                throw new RuntimeException(message);
            }

            log.info("Telegram OTP sent: userId={}, operationId={}, chatId={}",
                    userId, operationId, normalizedChatId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            log.error("Telegram request was interrupted: userId={}, operationId={}, chatId={}",
                    userId, operationId, normalizedChatId, e);

            throw new RuntimeException("Telegram request was interrupted");
        } catch (IOException e) {
            log.error("Telegram API is unavailable: userId={}, operationId={}, chatId={}",
                    userId, operationId, normalizedChatId, e);

            throw new RuntimeException("Telegram API is unavailable. Try again later.");
        }
    }

    private String buildSendMessageUrl(String chatId,
                                       Long userId,
                                       String operationId,
                                       String code,
                                       LocalDateTime expiresAt) {
        String message = "Your OTP code is: " + code
                + "\nOperation ID: " + operationId
                + "\nUser ID: " + userId
                + "\nExpires at: " + expiresAt;

        String encodedMessage = URLEncoder.encode(message, StandardCharsets.UTF_8);

        return telegramApiUrl
                + "/bot" + botToken
                + "/sendMessage?chat_id=" + chatId
                + "&text=" + encodedMessage;
    }

    private String resolveTelegramErrorMessage(int statusCode, String responseBody) {
        String body = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);

        if (statusCode == 400 && body.contains("chat not found")) {
            return "Telegram chat was not found. Complete Telegram binding again.";
        }

        if (statusCode == 403 && body.contains("bot was blocked by the user")) {
            return "Telegram bot is blocked by the user. Unblock the bot and try again.";
        }

        if (statusCode == 401) {
            return "Telegram bot configuration is invalid.";
        }

        if (statusCode >= 500) {
            return "Telegram API is unavailable. Try again later.";
        }

        return "Failed to send Telegram message";
    }
}