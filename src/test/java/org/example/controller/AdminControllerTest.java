package org.example.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.example.dto.LoggedInUserResponse;
import org.example.dto.UpdateOtpConfigRequest;
import org.example.model.OtpConfig;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.OtpConfigRepository;
import org.example.repository.UserRepository;
import org.example.security.RequestAuthService;
import org.example.service.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminController.class)
@Import(GlobalExceptionHandler.class)
class AdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserRepository userRepository;

  @MockitoBean private OtpConfigRepository otpConfigRepository;

  @MockitoBean private RequestAuthService requestAuthService;

  @MockitoBean private TokenService tokenService;

  @Test
  void getUsers_shouldReturnOk_whenAdminIsAuthorized() throws Exception {
    User user = new User();
    user.setId(2L);
    user.setLogin("user1");
    user.setRole(Role.USER);
    user.setEmail("user1@example.com");
    user.setPhone("+100000000");
    user.setTelegramChatId("12345");
    user.setCreatedAt(LocalDateTime.of(2026, 4, 22, 10, 0));

    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);
    when(userRepository.findAllNonAdmins()).thenReturn(List.of(user));

    mockMvc
        .perform(get("/admin/users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(2))
        .andExpect(jsonPath("$[0].login").value("user1"))
        .andExpect(jsonPath("$[0].role").value("USER"))
        .andExpect(jsonPath("$[0].email").value("user1@example.com"))
        .andExpect(jsonPath("$[0].phone").value("+100000000"))
        .andExpect(jsonPath("$[0].telegramChatId").value("12345"));
  }

  @Test
  void getUsers_shouldReturnForbidden_whenAccessDenied() throws Exception {
    when(requestAuthService.requireAdminUserId(any()))
        .thenThrow(new SecurityException("Access denied"));

    mockMvc
        .perform(get("/admin/users"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error").value("Access denied"));
  }

  @Test
  void deleteUser_shouldReturnOk_whenUserDeleted() throws Exception {
    User user = new User();
    user.setId(2L);
    user.setLogin("user1");
    user.setRole(Role.USER);

    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);
    when(userRepository.findById(2L)).thenReturn(user);
    when(userRepository.deleteById(2L)).thenReturn(true);

    mockMvc
        .perform(delete("/admin/users/2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("User deleted successfully"))
        .andExpect(jsonPath("$.userId").value(2));
  }

  @Test
  void getOtpConfig_shouldReturnOk_whenConfigExists() throws Exception {
    OtpConfig config = new OtpConfig();
    config.setId(1);
    config.setCodeLength(6);
    config.setTtlSeconds(120);

    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);
    when(otpConfigRepository.getConfig()).thenReturn(config);

    mockMvc
        .perform(get("/admin/otp-config"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.codeLength").value(6))
        .andExpect(jsonPath("$.ttlSeconds").value(120));
  }

  @Test
  void getOtpConfig_shouldReturnNotFound_whenConfigDoesNotExist() throws Exception {
    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);
    when(otpConfigRepository.getConfig()).thenReturn(null);

    mockMvc
        .perform(get("/admin/otp-config"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("OTP config not found"));
  }

  @Test
  void updateOtpConfig_shouldReturnOk_whenRequestIsValid() throws Exception {
    UpdateOtpConfigRequest request = new UpdateOtpConfigRequest();
    request.setCodeLength(6);
    request.setTtlSeconds(120);

    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);
    when(otpConfigRepository.updateConfig(6, 120)).thenReturn(true);

    mockMvc
        .perform(
            put("/admin/otp-config")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("OTP config updated successfully"))
        .andExpect(jsonPath("$.codeLength").value(6))
        .andExpect(jsonPath("$.ttlSeconds").value(120));
  }

  @Test
  void updateOtpConfig_shouldReturnBadRequest_whenCodeLengthIsInvalid() throws Exception {
    UpdateOtpConfigRequest request = new UpdateOtpConfigRequest();
    request.setCodeLength(3);
    request.setTtlSeconds(120);

    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);

    mockMvc
        .perform(
            put("/admin/otp-config")
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("Code length must be between 4 and 10"));
  }

  @Test
  void getLoggedInUsers_shouldReturnOk_whenAdminIsAuthorized() throws Exception {
    LoggedInUserResponse user =
        new LoggedInUserResponse(
            2L,
            "user1",
            Role.USER,
            LocalDateTime.of(2026, 4, 23, 10, 0),
            LocalDateTime.of(2026, 4, 23, 11, 0));

    when(requestAuthService.requireAdminUserId(any())).thenReturn(1L);
    when(tokenService.getLoggedInUsers()).thenReturn(List.of(user));

    mockMvc
        .perform(get("/admin/logged-in-users"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].userId").value(2))
        .andExpect(jsonPath("$[0].login").value("user1"))
        .andExpect(jsonPath("$[0].role").value("USER"));
  }
}
