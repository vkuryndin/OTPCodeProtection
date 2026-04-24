package org.example.service;

import org.example.repository.UserSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionCleanupServiceTest {

    private UserSessionRepository userSessionRepository;
    private SessionCleanupService sessionCleanupService;

    @BeforeEach
    void setUp() {
        userSessionRepository = mock(UserSessionRepository.class);
        sessionCleanupService = new SessionCleanupService(userSessionRepository);
    }

    @Test
    void cleanupExpiredSessions_shouldCallRepositoryCleanup() {
        when(userSessionRepository.cleanupExpiredSessions()).thenReturn(3);

        sessionCleanupService.cleanupExpiredSessions();

        verify(userSessionRepository).cleanupExpiredSessions();
    }

    @Test
    void cleanupExpiredSessions_shouldNotThrow_whenRepositoryFails() {
        doThrow(new RuntimeException("DB error"))
                .when(userSessionRepository)
                .cleanupExpiredSessions();

        assertDoesNotThrow(() -> sessionCleanupService.cleanupExpiredSessions());

        verify(userSessionRepository).cleanupExpiredSessions();
    }
}