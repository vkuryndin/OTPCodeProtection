package org.example.service;

import org.example.repository.UserSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SessionCleanupService {

  private static final Logger log = LoggerFactory.getLogger(SessionCleanupService.class);

  private final UserSessionRepository userSessionRepository;

  public SessionCleanupService(UserSessionRepository userSessionRepository) {
    this.userSessionRepository = userSessionRepository;
  }

  @Scheduled(fixedDelayString = "${session.cleanup.fixed-delay-ms:300000}")
  public void cleanupExpiredSessions() {
    try {
      int removed = userSessionRepository.cleanupExpiredSessions();

      if (removed > 0) {
        log.info("Expired or revoked user sessions removed: count={}", removed);
      }
    } catch (Exception e) {
      log.error("Failed to cleanup user sessions", e);
    }
  }
}
