package org.example.model;

import java.time.LocalDateTime;

public class OtpCode {
  private Long id;
  private Long userId;
  private String operationId;
  private String code;
  private OtpStatus status;
  private DeliveryChannel deliveryChannel;
  private String deliveryTarget;
  private LocalDateTime createdAt;
  private LocalDateTime expiresAt;
  private LocalDateTime sentAt;
  private LocalDateTime usedAt;

  public OtpCode() {}

  public OtpCode(
      Long id,
      Long userId,
      String operationId,
      String code,
      OtpStatus status,
      DeliveryChannel deliveryChannel,
      String deliveryTarget,
      LocalDateTime createdAt,
      LocalDateTime expiresAt,
      LocalDateTime sentAt,
      LocalDateTime usedAt) {
    this.id = id;
    this.userId = userId;
    this.operationId = operationId;
    this.code = code;
    this.status = status;
    this.deliveryChannel = deliveryChannel;
    this.deliveryTarget = deliveryTarget;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
    this.sentAt = sentAt;
    this.usedAt = usedAt;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUserId() {
    return userId;
  }

  public void setUserId(Long userId) {
    this.userId = userId;
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

  public OtpStatus getStatus() {
    return status;
  }

  public void setStatus(OtpStatus status) {
    this.status = status;
  }

  public DeliveryChannel getDeliveryChannel() {
    return deliveryChannel;
  }

  public void setDeliveryChannel(DeliveryChannel deliveryChannel) {
    this.deliveryChannel = deliveryChannel;
  }

  public String getDeliveryTarget() {
    return deliveryTarget;
  }

  public void setDeliveryTarget(String deliveryTarget) {
    this.deliveryTarget = deliveryTarget;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public LocalDateTime getSentAt() {
    return sentAt;
  }

  public void setSentAt(LocalDateTime sentAt) {
    this.sentAt = sentAt;
  }

  public LocalDateTime getUsedAt() {
    return usedAt;
  }

  public void setUsedAt(LocalDateTime usedAt) {
    this.usedAt = usedAt;
  }
}
