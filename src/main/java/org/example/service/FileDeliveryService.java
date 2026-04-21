package org.example.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

@Service
public class FileDeliveryService {

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
        } catch (IOException e) {
            throw new RuntimeException("Failed to save OTP to file", e);
        }
    }
}