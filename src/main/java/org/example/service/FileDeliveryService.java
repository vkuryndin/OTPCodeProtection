package org.example.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class FileDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(FileDeliveryService.class);

    public void saveOtpToFile(String fileName,
                              Long userId,
                              String operationId,
                              String code,
                              LocalDateTime expiresAt) {
        if (fileName == null || fileName.trim().isEmpty()) {
            throw new IllegalArgumentException("Delivery target is required");
        }

        Path path = Path.of(fileName.trim());

        String line = String.format(
                "%s | userId=%d | operationId=%s | code=%s | expiresAt=%s%n",
                LocalDateTime.now(),
                userId,
                operationId,
                code,
                expiresAt
        );

        try {
            Files.writeString(
                    path,
                    line,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );

            log.info("OTP saved to file: userId={}, operationId={}, file={}",
                    userId, operationId, path.toAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save OTP to file: userId={}, operationId={}, file={}",
                    userId, operationId, path.toAbsolutePath(), e);
            throw new RuntimeException("Failed to save OTP to file", e);
        }
    }
}