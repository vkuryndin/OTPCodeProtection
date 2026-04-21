package org.example.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.example.repository.OtpCodeRepository;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class OtpExpirationService {

    private final OtpCodeRepository otpCodeRepository;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public OtpExpirationService(OtpCodeRepository otpCodeRepository) {
        this.otpCodeRepository = otpCodeRepository;
    }

    @PostConstruct
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::expireCodesSafely,
                0,
                30,
                TimeUnit.SECONDS
        );
    }

    private void expireCodesSafely() {
        try {
            otpCodeRepository.expireActiveCodes();
        } catch (Exception e) {
            System.err.println("Failed to expire OTP codes: " + e.getMessage());
        }
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
    }
}