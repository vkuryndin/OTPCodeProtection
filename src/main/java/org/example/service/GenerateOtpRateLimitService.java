package org.example.service;

import org.example.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GenerateOtpRateLimitService {

    private static final Logger log = LoggerFactory.getLogger(GenerateOtpRateLimitService.class);

    private final boolean enabled;
    private final int maxAttempts;
    private final long windowSeconds;
    private final Map<Long, AttemptWindow> attemptsByUserId = new ConcurrentHashMap<>();

    public GenerateOtpRateLimitService(
            @Value("${otp.generate-rate-limit.enabled:false}") boolean enabled,
            @Value("${otp.generate-rate-limit.max-attempts:5}") int maxAttempts,
            @Value("${otp.generate-rate-limit.window-seconds:60}") long windowSeconds
    ) {
        this.enabled = enabled;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.windowSeconds = Math.max(1, windowSeconds);
    }

    public synchronized void validateAndRegisterAttempt(Long userId) {
        if (!enabled) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        AttemptWindow window = attemptsByUserId.get(userId);

        if (window == null || window.isExpired(now, windowSeconds)) {
            window = new AttemptWindow(now, 0);
            attemptsByUserId.put(userId, window);
        }

        if (window.attempts >= maxAttempts) {
            LocalDateTime availableAt = window.windowStartedAt.plusSeconds(windowSeconds);

            log.warn("OTP generation rate limit exceeded: userId={}, attempts={}, windowSeconds={}, availableAt={}",
                    userId, window.attempts, windowSeconds, availableAt);

            throw new RateLimitExceededException("Too many OTP generation requests. Try again later.");
        }

        window.attempts++;
    }

    @Scheduled(fixedDelayString = "${otp.generate-rate-limit.cleanup.fixed-delay-ms:600000}")
    public void cleanupExpiredWindows() {
        if (!enabled) {
            attemptsByUserId.clear();
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int before = attemptsByUserId.size();

        attemptsByUserId.entrySet().removeIf(entry -> entry.getValue().isExpired(now, windowSeconds));

        int removed = before - attemptsByUserId.size();
        if (removed > 0) {
            log.debug("Removed expired generate OTP rate limit windows: removed={}", removed);
        }
    }

    private static final class AttemptWindow {
        private final LocalDateTime windowStartedAt;
        private int attempts;

        private AttemptWindow(LocalDateTime windowStartedAt, int attempts) {
            this.windowStartedAt = windowStartedAt;
            this.attempts = attempts;
        }

        private boolean isExpired(LocalDateTime now, long windowSeconds) {
            return !windowStartedAt.plusSeconds(windowSeconds).isAfter(now);
        }
    }
}