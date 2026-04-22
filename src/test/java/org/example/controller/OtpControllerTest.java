package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.GenerateOtpRequest;
import org.example.dto.OtpGenerationResponse;
import org.example.dto.OtpValidationResponse;
import org.example.dto.ValidateOtpRequest;
import org.example.model.DeliveryChannel;
import org.example.model.OtpStatus;
import org.example.security.RequestAuthService;
import org.example.service.OtpService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OtpController.class)
@Import(GlobalExceptionHandler.class)
class OtpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OtpService otpService;

    @MockitoBean
    private RequestAuthService requestAuthService;

    @Test
    void generateOtp_shouldReturnCreated_whenRequestIsValid() throws Exception {
        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("op-123");
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget("otp.txt");

        OtpGenerationResponse response = new OtpGenerationResponse(
                "OTP generated successfully",
                10L,
                "op-123",
                OtpStatus.ACTIVE,
                DeliveryChannel.FILE,
                "otp.txt",
                LocalDateTime.of(2026, 4, 22, 12, 0)
        );

        when(requestAuthService.extractUserId(any())).thenReturn(1L);
        when(otpService.generateOtp(anyLong(), any(GenerateOtpRequest.class))).thenReturn(response);

        mockMvc.perform(post("/otp/generate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("OTP generated successfully"))
                .andExpect(jsonPath("$.otpId").value(10))
                .andExpect(jsonPath("$.operationId").value("op-123"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.deliveryChannel").value("FILE"))
                .andExpect(jsonPath("$.deliveryTarget").value("otp.txt"));
    }

    @Test
    void generateOtp_shouldReturnBadRequest_whenServiceThrowsValidationError() throws Exception {
        GenerateOtpRequest request = new GenerateOtpRequest();
        request.setOperationId("");
        request.setDeliveryChannel(DeliveryChannel.FILE);
        request.setDeliveryTarget("otp.txt");

        when(requestAuthService.extractUserId(any())).thenReturn(1L);
        when(otpService.generateOtp(anyLong(), any(GenerateOtpRequest.class)))
                .thenThrow(new IllegalArgumentException("Operation ID is required"));

        mockMvc.perform(post("/otp/generate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Operation ID is required"));
    }

    @Test
    void validateOtp_shouldReturnOk_whenRequestIsValid() throws Exception {
        ValidateOtpRequest request = new ValidateOtpRequest();
        request.setOperationId("op-123");
        request.setCode("123456");

        OtpValidationResponse response = new OtpValidationResponse(
                "OTP validated successfully",
                10L,
                "op-123",
                OtpStatus.USED
        );

        when(requestAuthService.extractUserId(any())).thenReturn(1L);
        when(otpService.validateOtp(anyLong(), any(ValidateOtpRequest.class))).thenReturn(response);

        mockMvc.perform(post("/otp/validate")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("OTP validated successfully"))
                .andExpect(jsonPath("$.otpId").value(10))
                .andExpect(jsonPath("$.operationId").value("op-123"))
                .andExpect(jsonPath("$.status").value("USED"));
    }
}