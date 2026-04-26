package org.example.security;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

  private static final int BCRYPT_COST = 12;

  public String hash(String rawPassword) {
    return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
  }

  public boolean matches(String rawPassword, String hashedPassword) {
    if (rawPassword == null || hashedPassword == null) {
      return false;
    }

    return BCrypt.checkpw(rawPassword, hashedPassword);
  }
}
