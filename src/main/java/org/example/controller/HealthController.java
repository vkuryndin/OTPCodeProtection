package org.example.controller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import org.example.repository.ConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

  @GetMapping("/health")
  public String health() {
    return "OK";
  }

  @GetMapping("/health/db")
  public ResponseEntity<Map<String, Object>> healthDb() {
    String sql = "SELECT current_database() AS database_name, current_user AS user_name";

    try (Connection connection = ConnectionFactory.getConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet rs = statement.executeQuery()) {

      Map<String, Object> response = new HashMap<>();
      response.put("status", "UP");

      if (rs.next()) {
        response.put("database", rs.getString("database_name"));
        response.put("user", rs.getString("user_name"));
      }

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      Map<String, Object> response = new HashMap<>();
      response.put("status", "DOWN");
      response.put("error", e.getMessage());
      return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
  }
}
