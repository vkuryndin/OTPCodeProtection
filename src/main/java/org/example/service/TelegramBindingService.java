package org.example.service;

import org.example.model.User;
import org.example.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class TelegramBindingService {

    private final UserRepository userRepository;
    private final EmailDeliveryService emailDeliveryService;
    private final String botUsername;
    private final long bindExpirationMinutes;

    private final String telegramApiUrl;
    private final String botToken;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TelegramBindingService(UserRepository userRepository,
                                  EmailDeliveryService emailDeliveryService,
                                  @Value("${telegram.bot-username}") String botUsername,
                                  @Value("${telegram.bind-expiration-minutes}") long bindExpirationMinutes,
                                  @Value("${telegram.api-url}") String telegramApiUrl,
                                  @Value("${telegram.bot-token}") String botToken) {
        this.userRepository = userRepository;
        this.emailDeliveryService = emailDeliveryService;
        this.botUsername = botUsername;
        this.bindExpirationMinutes = bindExpirationMinutes;
        this.telegramApiUrl = telegramApiUrl;
        this.botToken = botToken;
    }

    public Map<String, Object> startBinding(Long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (isBlank(user.getEmail())) {
            throw new IllegalArgumentException("User email is not set");
        }

        String bindToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(bindExpirationMinutes);

        userRepository.updateTelegramBindToken(userId, bindToken, expiresAt);

        String bindLink = "https://t.me/" + botUsername + "?start=" + bindToken;

        emailDeliveryService.sendTelegramBindEmail(user.getEmail(), bindLink, expiresAt);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Telegram bind link sent successfully");
        response.put("email", user.getEmail());
        response.put("expiresAt", expiresAt);

        return response;
    }
    public Map<String, Object> completeBinding(Long userId) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new IllegalArgumentException("User not found");
        }

        if (isBlank(user.getTelegramBindToken())) {
            throw new IllegalArgumentException("Telegram bind token is not set");
        }

        if (user.getTelegramBindExpiresAt() == null
                || user.getTelegramBindExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Telegram bind token expired");
        }

        String chatId = findChatIdByBindToken(user.getTelegramBindToken());
        if (isBlank(chatId)) {
            throw new IllegalArgumentException("Telegram chat id not found for bind token");
        }

        userRepository.bindTelegramChatId(userId, chatId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Telegram bound successfully");
        response.put("userId", userId);
        response.put("telegramChatId", chatId);

        return response;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String findChatIdByBindToken(String bindToken) {
        String url = telegramApiUrl + "/bot" + botToken + "/getUpdates";

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
                throw new RuntimeException("Failed to read Telegram updates");
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode result = root.get("result");

            if (result == null || !result.isArray()) {
                return null;
            }

            String expectedText = "/start " + bindToken;

            for (JsonNode update : result) {
                JsonNode message = update.get("message");
                if (message == null) {
                    continue;
                }

                JsonNode textNode = message.get("text");
                JsonNode chatNode = message.get("chat");

                if (textNode == null || chatNode == null) {
                    continue;
                }

                String text = textNode.asText();
                if (!expectedText.equals(text)) {
                    continue;
                }

                JsonNode chatIdNode = chatNode.get("id");
                if (chatIdNode != null) {
                    return chatIdNode.asText();
                }
            }

            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to read Telegram updates", e);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read Telegram updates", e);
        }
    }
}