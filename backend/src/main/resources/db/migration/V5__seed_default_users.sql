-- ==============================================================================
-- V5: Seed Default Enterprise Users
-- ==============================================================================

-- Password for both accounts is: password123 (BCrypt hash)
INSERT INTO app_user (username, email, password_hash, role, active)
VALUES 
    ('analyst1', 'analyst1@investops.com', '$2a$10$BBCS6B9LWGmnEJv.h8bzWO9JdaqkmBHu9.DN8JUh1M7q3.SH.jkc2', 'ANALYST', TRUE),
    ('admin1', 'admin1@investops.com', '$2a$10$BBCS6B9LWGmnEJv.h8bzWO9JdaqkmBHu9.DN8JUh1M7q3.SH.jkc2', 'ADMIN', TRUE)
ON CONFLICT (username) DO NOTHING;
