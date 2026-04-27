package org.example.model;

public class OtpConfig {
  private Integer id;
  private Integer codeLength;
  private Integer ttlSeconds;

  public OtpConfig() {}

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
}
