package org.example.controller;

import org.example.exception.UnauthorizedException;
import org.example.security.RequestAuthService;
import org.example.service.TelegramBindingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TelegramController.class)
@Import(GlobalExceptionHandler.class)
class TelegramControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramBindingService telegramBindingService;

    @MockitoBean
    private RequestAuthService requestAuthService;

    @Test
    void startBinding_shouldReturnOk_whenRequestIsValid() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Telegram bind link sent successfully");
        response.put("email", "user@example.com");
        response.put("expiresAt", "2026-04-22T12:00:00");

        when(requestAuthService.extractUserId(any())).thenReturn(1L);
        when(telegramBindingService.startBinding(1L)).thenReturn(response);

        mockMvc.perform(post("/telegram/bind/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Telegram bind link sent successfully"))
                .andExpect(jsonPath("$.email").value("user@example.com"));
    }

    @Test
    void completeBinding_shouldReturnOk_whenRequestIsValid() throws Exception {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("message", "Telegram bound successfully");
        response.put("userId", 1L);
        response.put("telegramChatId", "123456789");

        when(requestAuthService.extractUserId(any())).thenReturn(1L);
        when(telegramBindingService.completeBinding(1L)).thenReturn(response);

        mockMvc.perform(post("/telegram/bind/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Telegram bound successfully"))
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.telegramChatId").value("123456789"));
    }

    @Test
    void startBinding_shouldReturnUnauthorized_whenAuthFails() throws Exception {
        when(requestAuthService.extractUserId(any()))
                .thenThrow(new UnauthorizedException("Authorization header is required"));

        mockMvc.perform(post("/telegram/bind/start"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Authorization header is required"));
    }
}