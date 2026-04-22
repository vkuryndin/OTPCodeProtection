package org.example.repository;

import org.example.config.DbConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class ConnectionFactory {

    private static final DbConfig DB_CONFIG = new DbConfig();

    private ConnectionFactory() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                DB_CONFIG.getUrl(),
                DB_CONFIG.getUser(),
                DB_CONFIG.getPassword()
        );
    }
}