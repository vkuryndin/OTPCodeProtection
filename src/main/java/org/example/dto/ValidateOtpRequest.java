package org.example.dto;

public class ValidateOtpRequest {
    private String operationId;
    private String code;

    public ValidateOtpRequest() {
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}