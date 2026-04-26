package org.example.dto;

import org.example.model.DeliveryChannel;

public class GenerateOtpRequest {
  private String operationId;
  private DeliveryChannel deliveryChannel;
  private String deliveryTarget;

  public GenerateOtpRequest() {}

  public String getOperationId() {
    return operationId;
  }

  public void setOperationId(String operationId) {
    this.operationId = operationId;
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
}
