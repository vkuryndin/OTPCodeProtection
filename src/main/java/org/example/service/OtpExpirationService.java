package org.example.service;

import org.example.repository.OtpCodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class OtpExpirationService {

    private static final Logger log = LoggerFactory.getLogger(OtpExpirationService.class);

    private final OtpCodeRepository otpCodeRepository;

    public OtpExpirationService(OtpCodeRepository otpCodeRepository) {
        this.otpCodeRepository = otpCodeRepository;
    }

    @Scheduled(fixedRate = 30000)
    public void expireCodes() {
        try {
            otpCodeRepository.expireActiveCodes();
        } catch (Exception e) {
            log.error("Failed to expire OTP codes", e);
        }
    }
}