package org.example.service;

import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.BindType;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GeneralDataCoding;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.BindParameter;
import org.jsmpp.session.SMPPSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SmsDeliveryService {

    private final String host;
    private final int port;
    private final String systemId;
    private final String password;
    private final String systemType;
    private final String sourceAddr;

    public SmsDeliveryService(@Value("${smpp.host}") String host,
                              @Value("${smpp.port}") int port,
                              @Value("${smpp.system-id}") String systemId,
                              @Value("${smpp.password}") String password,
                              @Value("${smpp.system-type}") String systemType,
                              @Value("${smpp.source-addr}") String sourceAddr) {
        this.host = host;
        this.port = port;
        this.systemId = systemId;
        this.password = password;
        this.systemType = systemType;
        this.sourceAddr = sourceAddr;
    }

    public void sendOtpSms(String phone,
                           Long userId,
                           String operationId,
                           String code,
                           LocalDateTime expiresAt) {
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("User phone is not set");
        }

        SMPPSession session = new SMPPSession();

        try {
            BindParameter bindParameter = new BindParameter(
                    BindType.BIND_TX,
                    systemId,
                    password,
                    systemType,
                    TypeOfNumber.UNKNOWN,
                    NumberingPlanIndicator.UNKNOWN,
                    sourceAddr
            );

            session.connectAndBind(host, port, bindParameter);

            String text = "Your OTP code is: " + code
                    + ", operationId=" + operationId
                    + ", userId=" + userId
                    + ", expiresAt=" + expiresAt;

            session.submitShortMessage(
                    systemType,
                    TypeOfNumber.UNKNOWN,
                    NumberingPlanIndicator.UNKNOWN,
                    sourceAddr,
                    TypeOfNumber.UNKNOWN,
                    NumberingPlanIndicator.UNKNOWN,
                    phone.trim(),
                    new ESMClass(),
                    (byte) 0,
                    (byte) 1,
                    null,
                    null,
                    new RegisteredDelivery(SMSCDeliveryReceipt.DEFAULT),
                    (byte) 0,
                    new GeneralDataCoding(Alphabet.ALPHA_DEFAULT),
                    (byte) 0,
                    text.getBytes(StandardCharsets.UTF_8)
            );

            log.info("OTP SMS sent: userId={}, operationId={}, phone={}",
                    userId, operationId, phone.trim());
        } catch (java.io.IOException e) {
            log.error("Failed to send OTP SMS: SMPP simulator unavailable, userId={}, operationId={}, phone={}",
                    userId, operationId, phone, e);
            throw new RuntimeException("SMPP simulator is not available. Start the SMPP server and try again.", e);
        } catch (Exception e) {
            log.error("Failed to send OTP SMS: userId={}, operationId={}, phone={}",
                    userId, operationId, phone, e);
            throw new RuntimeException("Failed to send SMS", e);
        } finally {
            try {
                session.unbindAndClose();
            } catch (Exception ignored) {
            }
        }
    }
}