package com.factoryguard.guard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Accounts and sign-in sessions. Passwords are stored as BCrypt hashes, never as text. */
@Service
public class AuthService {

    public record User(long id, String username) {
    }

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,30}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int MAX_FAILED_LOGINS = 5;
    private static final long FAIL_WINDOW_MS = 5 * 60 * 1000L;

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Long>> failedLogins = new HashMap<>();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public User register(String username, String email, String password) {
        username = username == null ? "" : username.trim();
        email = email == null ? "" : email.trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 characters: letters, numbers, dot, dash or underscore.");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 120) {
            throw new IllegalArgumentException("Please enter a valid email address.");
        }
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("Password must be 8 to 72 characters.");
        }
        Integer taken = jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(username) = lower(?)", Integer.class, username);
        if (taken != null && taken > 0) {
            throw new IllegalArgumentException("That username is already taken.");
        }
        long id = jdbc.queryForObject(
                "INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?) RETURNING id",
                Long.class, username, email, encoder.encode(password));
        return new User(id, username);
    }

    public User login(String username, String password) {
        String key = username == null ? "" : username.trim().toLowerCase();
        if (tooManyFailures(key)) {
            throw new IllegalStateException("Too many failed attempts. Please wait a few minutes and try again.");
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, password_hash FROM users WHERE lower(username) = lower(?)", key);
        if (rows.isEmpty() || password == null || !encoder.matches(password, (String) rows.get(0).get("password_hash"))) {
            recordFailure(key);
            throw new IllegalArgumentException("Wrong username or password.");
        }
        Map<String, Object> row = rows.get(0);
        return new User(((Number) row.get("id")).longValue(), (String) row.get("username"));
    }

    public String createSession(long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("DELETE FROM sessions WHERE expires_at < now()");
        jdbc.update("INSERT INTO sessions (token_hash, user_id, expires_at) VALUES (?, ?, now() + interval '7 days')",
                hash(token), userId);
        return token;
    }

    public Optional<User> userForToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT u.id, u.username FROM sessions s JOIN users u ON u.id = s.user_id "
                        + "WHERE s.token_hash = ? AND s.expires_at > now()", hash(token));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> row = rows.get(0);
        return Optional.of(new User(((Number) row.get("id")).longValue(), (String) row.get("username")));
    }

    public void deleteSession(String token) {
        if (token != null) {
            jdbc.update("DELETE FROM sessions WHERE token_hash = ?", hash(token));
        }
    }

    private synchronized boolean tooManyFailures(String key) {
        Deque<Long> q = failedLogins.get(key);
        if (q == null) {
            return false;
        }
        long cutoff = System.currentTimeMillis() - FAIL_WINDOW_MS;
        while (!q.isEmpty() && q.peekFirst() < cutoff) {
            q.removeFirst();
        }
        return q.size() >= MAX_FAILED_LOGINS;
    }

    private synchronized void recordFailure(String key) {
        failedLogins.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(System.currentTimeMillis());
    }

    private static String hash(String token) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
