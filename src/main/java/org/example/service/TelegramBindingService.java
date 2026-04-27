package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TelegramBindingService {

  private static final Logger log = LoggerFactory.getLogger(TelegramBindingService.class);

  private final UserRepository userRepository;
  private final EmailDeliveryService emailDeliveryService;
  private final String botUsername;
  private final long bindExpirationMinutes;
  private final String telegramApiUrl;
  private final String botToken;
  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  public TelegramBindingService(
      UserRepository userRepository,
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

  // Starts Telegram binding by generating a temporary bind token
  // and sending the user a link to the bot.
  public Map<String, Object> startBinding(Long userId) {
    User user = requireUser(userId);

    if (user.getEmail() == null || user.getEmail().isBlank()) {
      log.warn(
          "Telegram bind start failed: email is not set, userId={}, login={}",
          userId,
          user.getLogin());
      throw new IllegalArgumentException("User email is not set");
    }

    String bindToken = UUID.randomUUID().toString();
    LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(bindExpirationMinutes);
    String bindLink = "https://t.me/" + botUsername + "?start=" + bindToken;

    userRepository.updateTelegramBindToken(userId, bindToken, expiresAt);
    emailDeliveryService.sendTelegramBindEmail(user.getEmail(), bindLink, expiresAt);

    log.info(
        "Telegram bind started: userId={}, login={}, email={}, expiresAt={}",
        userId,
        user.getLogin(),
        user.getEmail(),
        expiresAt);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("message", "Telegram bind link sent successfully");
    response.put("email", user.getEmail());
    response.put("expiresAt", expiresAt);
    return response;
  }

  // Completes Telegram binding after the user opens the bot link.
  // On success the user's telegram_chat_id is stored in the database.
  public Map<String, Object> completeBinding(Long userId) {
    User user = requireUser(userId);

    if (user.getTelegramBindToken() == null || user.getTelegramBindToken().isBlank()) {
      log.warn(
          "Telegram bind complete failed: bind token is not set, userId={}, login={}",
          userId,
          user.getLogin());
      throw new IllegalArgumentException("Telegram bind token is not set");
    }

    if (user.getTelegramBindExpiresAt() == null
        || user.getTelegramBindExpiresAt().isBefore(LocalDateTime.now())) {
      log.warn(
          "Telegram bind complete failed: bind token expired, userId={}, login={}",
          userId,
          user.getLogin());
      throw new IllegalArgumentException("Telegram bind token expired");
    }

    String chatId = findChatIdByBindToken(user.getTelegramBindToken());
    if (chatId == null || chatId.isBlank()) {
      log.warn(
          "Telegram bind complete failed: chat id not found, userId={}, login={}",
          userId,
          user.getLogin());
      throw new IllegalArgumentException("Telegram chat id not found for bind token");
    }

    userRepository.bindTelegramChatId(userId, chatId);

    log.info(
        "Telegram bound successfully: userId={}, login={}, chatId={}",
        userId,
        user.getLogin(),
        chatId);

    Map<String, Object> response = new LinkedHashMap<>();
    response.put("message", "Telegram bound successfully");
    response.put("userId", userId);
    response.put("telegramChatId", chatId);
    return response;
  }

  private User requireUser(Long userId) {
    User user = userRepository.findById(userId);
    if (user == null) {
      log.warn("Telegram bind start failed: user not found, userId={}", userId);
      throw new IllegalArgumentException("User not found");
    }
    return user;
  }

  private String findChatIdByBindToken(String bindToken) {
    String url = telegramApiUrl + "/bot" + botToken + "/getUpdates";

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());

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
