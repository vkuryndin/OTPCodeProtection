package org.example.integration.support;

import org.example.model.Role;
import org.example.model.User;

public final class TestUsers {

    private TestUsers() {
    }

    public static User user(String login, String passwordHash) {
        User user = new User();
        user.setLogin(login);
        user.setPasswordHash(passwordHash);
        user.setRole(Role.USER);
        user.setEmail(login + "@test.com");
        user.setPhone("+37400112233");
        user.setTelegramChatId("123456789");
        return user;
    }

    public static User admin(String login, String passwordHash) {
        User admin = new User();
        admin.setLogin(login);
        admin.setPasswordHash(passwordHash);
        admin.setRole(Role.ADMIN);
        admin.setEmail(login + "@test.com");
        admin.setPhone("+37400110000");
        return admin;
    }
}