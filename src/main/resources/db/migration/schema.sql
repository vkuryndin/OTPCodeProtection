-- =========================================
-- ENUM TYPES
-- =========================================
CREATE TYPE user_role AS ENUM ('ADMIN', 'USER');
CREATE TYPE otp_status AS ENUM ('ACTIVE', 'EXPIRED', 'USED');
CREATE TYPE delivery_channel AS ENUM ('EMAIL', 'SMS', 'TELEGRAM', 'FILE');


-- =========================================
-- USERS
-- =========================================
CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       login VARCHAR(100) NOT NULL,
                       password_hash VARCHAR(255) NOT NULL,
                       role user_role NOT NULL,
                       email VARCHAR(255),
                       phone VARCHAR(30),
                       telegram_chat_id VARCHAR(100),
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                       telegram_bind_token VARCHAR(255),
                       telegram_bind_expires_at TIMESTAMP
);

CREATE UNIQUE INDEX ux_users_login_lower
    ON users (LOWER(login));

CREATE UNIQUE INDEX ux_users_single_admin
    ON users (role)
    WHERE role = 'ADMIN';

-- =========================================
-- USER SESSIONS
-- =========================================
CREATE TABLE user_sessions (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL,
                               token_id VARCHAR(64) NOT NULL UNIQUE,
                               logged_in_at TIMESTAMP NOT NULL,
                               expires_at TIMESTAMP NOT NULL,
                               revoked_at TIMESTAMP,
                               CONSTRAINT fk_user_sessions_user
                                   FOREIGN KEY (user_id)
                                       REFERENCES users(id)
                                       ON DELETE CASCADE
);

CREATE INDEX idx_user_sessions_user_id
    ON user_sessions (user_id);

CREATE INDEX idx_user_sessions_expires_at
    ON user_sessions (expires_at);

CREATE INDEX idx_user_sessions_revoked_at
    ON user_sessions (revoked_at);

-- =========================================
-- OTP CONFIG
-- =========================================
CREATE TABLE otp_config (
                            id INT PRIMARY KEY CHECK (id = 1),
                            code_length INT NOT NULL CHECK (code_length BETWEEN 4 AND 10),
                            ttl_seconds INT NOT NULL CHECK (ttl_seconds > 0),
                            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO otp_config (id, code_length, ttl_seconds, updated_at)
VALUES (1, 6, 300, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- =========================================
-- OTP CODES
-- =========================================
CREATE TABLE otp_codes (
                           id BIGSERIAL PRIMARY KEY,
                           user_id BIGINT NOT NULL,
                           operation_id VARCHAR(100) NOT NULL,
                           code VARCHAR(20) NOT NULL,
                           status otp_status NOT NULL,
                           delivery_channel delivery_channel NOT NULL,
                           delivery_target VARCHAR(255) NOT NULL,
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                           expires_at TIMESTAMP NOT NULL,
                           sent_at TIMESTAMP,
                           used_at TIMESTAMP,
                           CONSTRAINT fk_otp_codes_user
                               FOREIGN KEY (user_id)
                                   REFERENCES users(id)
                                   ON DELETE CASCADE
);

CREATE INDEX idx_otp_codes_user_id
    ON otp_codes (user_id);

CREATE INDEX idx_otp_codes_operation_id
    ON otp_codes (operation_id);

CREATE INDEX idx_otp_codes_status
    ON otp_codes (status);

CREATE INDEX idx_otp_codes_expires_at
    ON otp_codes (expires_at);

CREATE INDEX idx_otp_codes_user_operation
    ON otp_codes (user_id, operation_id);

CREATE INDEX idx_otp_codes_status_expires_at ON otp_codes (status, expires_at);