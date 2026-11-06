package com.factoryguard.guard;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/** Every /api/ call needs a signed-in session, except register and login. */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public static final String COOKIE = "fg_session";
    public static final String USER_ATTRIBUTE = "fgUser";

    private final AuthService auth;

    public AuthFilter(AuthService auth) {
        this.auth = auth;
    }

    public static String tokenFrom(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String p = request.getRequestURI();
        return !p.startsWith("/api/")
                || p.equals("/api/auth/login")
                || p.equals("/api/auth/register");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        Optional<AuthService.User> user = auth.userForToken(tokenFrom(req));
        if (user.isEmpty()) {
            res.setStatus(401);
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Please sign in.\"}");
            return;
        }
        req.setAttribute(USER_ATTRIBUTE, user.get());
        chain.doFilter(req, res);
    }
}
