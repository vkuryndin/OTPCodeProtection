package org.example.config;

import java.io.InputStream;
import java.util.Properties;

public final class DbConfig {

    private volatile Properties properties;

    public String getUrl() {
        return getProperties().getProperty("db.url");
    }

    public String getUser() {
        return getProperties().getProperty("db.user");
    }

    public String getPassword() {
        return getProperties().getProperty("db.password");
    }

    private Properties getProperties() {
        Properties loaded = properties;

        if (loaded == null) {
            synchronized (this) {
                loaded = properties;
                if (loaded == null) {
                    loaded = loadProperties();
                    properties = loaded;
                }
            }
        }

        return loaded;
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