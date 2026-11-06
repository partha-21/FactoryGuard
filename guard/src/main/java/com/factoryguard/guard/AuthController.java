package com.factoryguard.guard;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final boolean cookieSecure;

    /** Set GUARD_COOKIE_SECURE=true when the dashboard is served over HTTPS. */
    public AuthController(AuthService auth, @Value("${guard.cookie-secure:false}") boolean cookieSecure) {
        this.auth = auth;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        try {
            AuthService.User u = auth.register(body.get("username"), body.get("email"), body.get("password"));
            return signedIn(u);
        } catch (IllegalArgumentException e) {
            return error(400, e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        try {
            return signedIn(auth.login(body.get("username"), body.get("password")));
        } catch (IllegalArgumentException e) {
            return error(401, e.getMessage());
        } catch (IllegalStateException e) {
            return error(429, e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest req) {
        auth.deleteSession(AuthFilter.tokenFrom(req));
        ResponseCookie cleared = ResponseCookie.from(AuthFilter.COOKIE, "")
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(0).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cleared.toString()).body(Map.of("message", "Signed out"));
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpServletRequest req) {
        AuthService.User u = (AuthService.User) req.getAttribute(AuthFilter.USER_ATTRIBUTE);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.id());
        m.put("username", u.username());
        return m;
    }

    private ResponseEntity<Map<String, Object>> signedIn(AuthService.User u) {
        String token = auth.createSession(u.id());
        ResponseCookie cookie = ResponseCookie.from(AuthFilter.COOKIE, token)
                .httpOnly(true).secure(cookieSecure).sameSite("Lax").path("/").maxAge(Duration.ofDays(7)).build();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", u.id());
        body.put("username", u.username());
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookie.toString()).body(body);
    }

    private ResponseEntity<Map<String, Object>> error(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
