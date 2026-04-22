package org.example.config;

import java.io.InputStream;
import java.util.Properties;

public class DbConfig {

    private final Properties properties;

    public DbConfig() {
        this.properties = loadProperties();
    }

    public String getUrl() {
        return properties.getProperty("db.url");
    }

    public String getUser() {
        return properties.getProperty("db.user");
    }

    public String getPassword() {
        return properties.getProperty("db.password");
    }

    private Properties loadProperties() {
        Properties loadedProperties = new Properties();

        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            if (inputStream == null) {
                throw new RuntimeException("db.properties not found");
            }

            loadedProperties.load(inputStream);
            return loadedProperties;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load database configuration", e);
        }
    }
}