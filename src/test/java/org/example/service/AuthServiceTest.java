package org.example.service;

import org.example.dto.RegisterRequest;
import org.example.model.Role;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.security.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordHasher passwordHasher;


    @InjectMocks
    private AuthService authService;

    @Test
    void register_shouldCreateUser_whenRequestIsValid() {
        RegisterRequest request = new RegisterRequest();
        request.setLogin("user_1");
        request.setPassword("12345678");
        request.setRole(Role.USER);
        request.setEmail("user_1@test.com");
        request.setPhone("+37400112233");
        request.setTelegramChatId("123456789");

        when(userRepository.findByLogin("user_1")).thenReturn(null);
        when(passwordHasher.hash("12345678")).thenReturn("hashed_password");
        when(userRepository.createUser(any(User.class))).thenReturn(10L);

        Long userId = authService.register(request);

        assertEquals(10L, userId);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).createUser(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals("user_1", savedUser.getLogin());
        assertEquals("hashed_password", savedUser.getPasswordHash());
        assertEquals(Role.USER, savedUser.getRole());
        assertEquals("user_1@test.com", savedUser.getEmail());
        assertEquals("+37400112233", savedUser.getPhone());
        assertEquals("123456789", savedUser.getTelegramChatId());

        verify(passwordHasher).hash("12345678");
    }

    @Test
    void register_shouldThrowIllegalArgumentException_whenLoginAlreadyExists() {
        RegisterRequest request = new RegisterRequest();
        request.setLogin("duplicate_user");
        request.setPassword("12345678");
        request.setRole(Role.USER);

        User existingUser = new User();
        existingUser.setId(1L);
        existingUser.setLogin("duplicate_user");

        when(userRepository.findByLogin("duplicate_user")).thenReturn(existingUser);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.register(request)
        );

        assertEquals("User with this login already exists", exception.getMessage());

        verify(userRepository, never()).createUser(any(User.class));
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void register_shouldThrowIllegalStateException_whenSecondAdminIsCreated() {
        RegisterRequest request = new RegisterRequest();
        request.setLogin("second_admin");
        request.setPassword("12345678");
        request.setRole(Role.ADMIN);

        when(userRepository.findByLogin("second_admin")).thenReturn(null);
        when(userRepository.adminExists()).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> authService.register(request)
        );

        assertEquals("Admin already exists", exception.getMessage());

        verify(userRepository, never()).createUser(any(User.class));
        verify(passwordHasher, never()).hash(any());
    }

    @Test
    void authenticate_shouldReturnUser_whenCredentialsAreValid() {
        User user = new User();
        user.setId(5L);
        user.setLogin("valid_user");
        user.setPasswordHash("hashed_password");
        user.setRole(Role.USER);

        when(userRepository.findByLogin("valid_user")).thenReturn(user);
        when(passwordHasher.matches("12345678", "hashed_password")).thenReturn(true);

        User result = authService.authenticate("valid_user", "12345678");

        assertNotNull(result);
        assertEquals(5L, result.getId());
        assertEquals("valid_user", result.getLogin());
        assertEquals(Role.USER, result.getRole());
    }

    @Test
    void authenticate_shouldThrowIllegalArgumentException_whenPasswordIsWrong() {
        User user = new User();
        user.setId(6L);
        user.setLogin("user_wrong_password");
        user.setPasswordHash("hashed_password");

        when(userRepository.findByLogin("user_wrong_password")).thenReturn(user);
        when(passwordHasher.matches("wrongpass", "hashed_password")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.authenticate("user_wrong_password", "wrongpass")
        );

        assertEquals("Invalid login or password", exception.getMessage());
    }

    @Test
    void authenticate_shouldThrowIllegalArgumentException_whenUserNotFound() {
        when(userRepository.findByLogin("missing_user")).thenReturn(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> authService.authenticate("missing_user", "12345678")
        );

        assertEquals("Invalid login or password", exception.getMessage());
    }
}