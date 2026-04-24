package org.example.integration;

import org.example.model.User;
import org.example.integration.support.TestHttpAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;
import java.util.stream.Stream;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OtpRequestValidationApiITTest extends BaseIntegrationTest {

    private String testLogin;

    @BeforeEach
    void setUp() {
        testLogin = "it_otp_req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        deleteUserByLogin(testLogin);

        User user = createUser(testLogin);
        userRepository.createUser(user);

        resetOtpConfig();
    }

    @AfterEach
    void tearDown() {
        deleteUserByLogin(testLogin);
        resetOtpConfig();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidGenerateRequests")
    void generateOtp_shouldRejectInvalidRequest(String caseName,
                                                String requestBody,
                                                String expectedError) throws Exception {
        String token = loginAndGetToken(testLogin);

        ResponseEntity<String> response = postAuthorizedJson("/otp/generate", token, requestBody);

        TestHttpAssertions.assertError(response, HttpStatus.BAD_REQUEST, expectedError, objectMapper);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidValidateRequests")
    void validateOtp_shouldRejectInvalidRequest(String caseName,
                                                String requestBody,
                                                String expectedError) throws Exception {
        String token = loginAndGetToken(testLogin);

        ResponseEntity<String> response = postAuthorizedJson("/otp/validate", token, requestBody);

        TestHttpAssertions.assertError(response, HttpStatus.BAD_REQUEST, expectedError, objectMapper);
    }

    private static Stream<Arguments> invalidGenerateRequests() {
        return Stream.of(
                Arguments.of(
                        "missing operationId",
                        """
                        {
                          "deliveryChannel": "FILE",
                          "deliveryTarget": "otp-codes.txt"
                        }
                        """,
                        "Operation ID is required"
                ),
                Arguments.of(
                        "missing delivery channel",
                        """
                        {
                          "operationId": "payment-missing-channel-001",
                          "deliveryTarget": "otp-codes.txt"
                        }
                        """,
                        "Delivery channel is required"
                )
        );
    }

    private static Stream<Arguments> invalidValidateRequests() {
        return Stream.of(
                Arguments.of(
                        "missing operationId",
                        """
                        {
                          "code": "123456"
                        }
                        """,
                        "Operation ID is required"
                ),
                Arguments.of(
                        "missing code",
                        """
                        {
                          "operationId": "payment-missing-code-001"
                        }
                        """,
                        "OTP code is required"
                )
        );
    }
}