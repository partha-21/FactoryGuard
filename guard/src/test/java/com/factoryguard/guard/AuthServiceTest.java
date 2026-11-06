package com.factoryguard.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Input checks happen before the database is touched, so no database is needed here. */
class AuthServiceTest {

    private final AuthService auth = new AuthService(null);

    private String messageFor(String username, String email, String password) {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> auth.register(username, email, password));
        return e.getMessage();
    }

    @Test
    void rejectsBadUsername() {
        assertTrue(messageFor("ab", "a@b.co", "longenough1").toLowerCase().contains("username"));
        assertTrue(messageFor("has space", "a@b.co", "longenough1").toLowerCase().contains("username"));
        assertTrue(messageFor(null, "a@b.co", "longenough1").toLowerCase().contains("username"));
    }

    @Test
    void rejectsBadEmail() {
        assertTrue(messageFor("valid_user", "not-an-email", "longenough1").toLowerCase().contains("email"));
        assertTrue(messageFor("valid_user", "", "longenough1").toLowerCase().contains("email"));
    }

    @Test
    void rejectsShortPassword() {
        assertTrue(messageFor("valid_user", "a@b.co", "short").toLowerCase().contains("password"));
        assertTrue(messageFor("valid_user", "a@b.co", null).toLowerCase().contains("password"));
    }
}
