package org.example.model;

import java.time.LocalDateTime;

public class OtpConfig {
    private Integer id;
    private Integer codeLength;
    private Integer ttlSeconds;
    private LocalDateTime updatedAt;

    public OtpConfig() {
    }

    public OtpConfig(Integer id, Integer codeLength, Integer ttlSeconds, LocalDateTime updatedAt) {
        this.id = id;
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
        this.updatedAt = updatedAt;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
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

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}