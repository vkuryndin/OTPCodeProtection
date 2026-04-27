package org.example.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Map;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramBindingServiceTest {

  @Mock private UserRepository userRepository;

  @Mock private EmailDeliveryService emailDeliveryService;

  private TelegramBindingService telegramBindingService;

  @BeforeEach
  void setUp() {
    telegramBindingService =
        new TelegramBindingService(
            userRepository,
            emailDeliveryService,
            "my_test_bot",
            15L,
            "https://api.telegram.org",
            "fake-bot-token");
  }

  @Test
  void startBinding_shouldGenerateTokenAndSendEmail_whenUserIsValid() {
    User user = new User();
    user.setId(1L);
    user.setLogin("user1");
    user.setEmail("user1@test.com");

    when(userRepository.findById(1L)).thenReturn(user);

    Object response = telegramBindingService.startBinding(1L);

    assertEquals("Telegram bind link sent successfully", readValue(response, "message"));
    assertEquals("user1@test.com", readValue(response, "email"));
    assertNotNull(readValue(response, "expiresAt"));

    ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<LocalDateTime> expiresCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

    verify(userRepository)
        .updateTelegramBindToken(eq(1L), tokenCaptor.capture(), expiresCaptor.capture());

    String bindToken = tokenCaptor.getValue();
    LocalDateTime expiresAt = expiresCaptor.getValue();

    assertNotNull(bindToken);
    assertFalse(bindToken.isBlank());
    assertNotNull(expiresAt);

    ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);

    verify(emailDeliveryService)
        .sendTelegramBindEmail(eq("user1@test.com"), linkCaptor.capture(), eq(expiresAt));

    String bindLink = linkCaptor.getValue();
    assertNotNull(bindLink);
    assertTrue(bindLink.contains("https://t.me/my_test_bot?start="));
    assertTrue(bindLink.contains(bindToken));
  }

  @Test
  void startBinding_shouldThrowIllegalArgumentException_whenUserNotFound() {
    when(userRepository.findById(10L)).thenReturn(null);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> telegramBindingService.startBinding(10L));

    assertEquals("User not found", exception.getMessage());

    verify(userRepository, never()).updateTelegramBindToken(anyLong(), anyString(), any());
    verifyNoInteractions(emailDeliveryService);
  }

  @Test
  void startBinding_shouldThrowIllegalArgumentException_whenUserEmailIsMissing() {
    User user = new User();
    user.setId(2L);
    user.setLogin("user_no_email");
    user.setEmail(null);

    when(userRepository.findById(2L)).thenReturn(user);

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> telegramBindingService.startBinding(2L));

    assertEquals("User email is not set", exception.getMessage());

    verify(userRepository, never()).updateTelegramBindToken(anyLong(), anyString(), any());
    verifyNoInteractions(emailDeliveryService);
  }

  @Test
  void completeBinding_shouldThrowIllegalArgumentException_whenUserNotFound() {
    when(userRepository.findById(3L)).thenReturn(null);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> telegramBindingService.completeBinding(3L));

    assertEquals("User not found", exception.getMessage());

    verify(userRepository, never()).bindTelegramChatId(anyLong(), anyString());
  }

  @Test
  void completeBinding_shouldThrowIllegalArgumentException_whenBindTokenIsMissing() {
    User user = new User();
    user.setId(4L);
    user.setLogin("user_no_token");
    user.setTelegramBindToken(null);

    when(userRepository.findById(4L)).thenReturn(user);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> telegramBindingService.completeBinding(4L));

    assertEquals("Telegram bind token is not set", exception.getMessage());

    verify(userRepository, never()).bindTelegramChatId(anyLong(), anyString());
  }

  @Test
  void completeBinding_shouldThrowIllegalArgumentException_whenBindTokenIsExpired() {
    User user = new User();
    user.setId(5L);
    user.setLogin("user_expired");
    user.setTelegramBindToken("expired-token");
    user.setTelegramBindExpiresAt(LocalDateTime.now().minusMinutes(5));

    when(userRepository.findById(5L)).thenReturn(user);

    IllegalArgumentException exception =
        assertThrows(
            IllegalArgumentException.class, () -> telegramBindingService.completeBinding(5L));

    assertEquals("Telegram bind token expired", exception.getMessage());

    verify(userRepository, never()).bindTelegramChatId(anyLong(), anyString());
  }

  private Object readValue(Object target, String propertyName) {
    if (target instanceof Map<?, ?> map) {
      return map.get(propertyName);
    }

    try {
      String capitalized =
          Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);

      try {
        Method getter = target.getClass().getMethod("get" + capitalized);
        return getter.invoke(target);
      } catch (NoSuchMethodException ignored) {
        Method accessor = target.getClass().getMethod(propertyName);
        return accessor.invoke(target);
      }
    } catch (Exception e) {
      fail(
          "Failed to read property '"
              + propertyName
              + "' from "
              + target.getClass().getSimpleName()
              + ": "
              + e.getMessage());
      return null;
    }
  }
}
