package org.example.dto;

public class UpdateOtpConfigRequest {
    private Integer codeLength;
    private Integer ttlSeconds;

    public UpdateOtpConfigRequest() {
    }

    public Integer getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(Integer codeLength) {
        this.codeLength = codeLength;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Integer ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}