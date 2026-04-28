package org.example.integration.support;

import org.example.dto.GenerateOtpRequest;
import org.example.dto.LoginRequest;
import org.example.dto.ValidateOtpRequest;
import org.example.model.DeliveryChannel;

public final class TestRequests {

  private TestRequests() {}

  public static LoginRequest login(String login, String password) {
    LoginRequest request = new LoginRequest();
    request.setLogin(login);
    request.setPassword(password);
    return request;
  }

  public static GenerateOtpRequest generateFileOtp(String operationId, String filePath) {
    GenerateOtpRequest request = new GenerateOtpRequest();
    request.setOperationId(operationId);
    request.setDeliveryChannel(DeliveryChannel.FILE);
    request.setDeliveryTarget(filePath);
    return request;
  }

  public static ValidateOtpRequest validateOtp(String operationId, String code) {
    ValidateOtpRequest request = new ValidateOtpRequest();
    request.setOperationId(operationId);
    request.setCode(code);
    return request;
  }
}
