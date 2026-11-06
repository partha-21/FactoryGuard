package com.factoryguard.guard;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The API the dashboard talks to. Everything is scoped to the signed-in user. */
@RestController
@RequestMapping("/api")
public class GuardController {

    private final MonitoringService monitoring;
    private final DeploymentService deployment;
    private final RecoveryService recovery;
    private final JdbcTemplate jdbc;
    private final GuardConfig cfg;

    public GuardController(MonitoringService monitoring, DeploymentService deployment, RecoveryService recovery,
                           JdbcTemplate jdbc, GuardConfig cfg) {
        this.monitoring = monitoring;
        this.deployment = deployment;
        this.recovery = recovery;
        this.jdbc = jdbc;
        this.cfg = cfg;
    }

    private static AuthService.User user(HttpServletRequest req) {
        return (AuthService.User) req.getAttribute(AuthFilter.USER_ATTRIBUTE);
    }

    private boolean isMine(AuthService.User u) {
        return deployment.isDeployed() && Objects.equals(deployment.ownerId(), u.id());
    }

    /** Live status. A user who has not started anything sees zeros. */
    @GetMapping("/status")
    public Map<String, Object> status(HttpServletRequest req) {
        AuthService.User u = user(req);
        boolean mine = isMine(u);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("started", mine);
        m.put("busyWithOther", !mine && deployment.isDeployed());
        m.put("threshold", cfg.threshold());
        m.put("warmupSeconds", cfg.warmupSeconds());
        m.put("failuresBeforeRollback", cfg.failuresBeforeRollback());
        m.put("maxRollbacks", cfg.maxRollbacksPer10Min());
        m.put("checkIntervalSeconds", cfg.checkIntervalSeconds());
        if (!mine) {
            m.put("state", "NOT_STARTED");
            m.put("lastAction", "");
            m.put("currentVersion", "-");
            m.put("lastKnownGood", "-");
            m.put("secondsSinceDeploy", 0);
            m.put("failStreak", 0);
            m.put("rollbacksInWindow", 0);
            m.put("latest", null);
            m.put("samples", new ArrayList<Sample>());
            return m;
        }
        deployment.markOwnerActive();
        List<Sample> samples = monitoring.recentSamples();
        m.put("state", monitoring.state());
        m.put("lastAction", monitoring.lastAction());
        m.put("currentVersion", deployment.currentVersion());
        m.put("lastKnownGood", deployment.lastKnownGood());
        m.put("secondsSinceDeploy", deployment.secondsSinceDeploy());
        m.put("failStreak", monitoring.failStreak());
        m.put("rollbacksInWindow", recovery.recentRollbackCount());
        m.put("latest", samples.isEmpty() ? null : samples.get(samples.size() - 1));
        m.put("samples", samples.size() > 60 ? samples.subList(samples.size() - 60, samples.size()) : samples);
        return m;
    }

    /** Totals for this user. A brand-new user gets all zeros. */
    @GetMapping("/summary")
    public Map<String, Object> summary(HttpServletRequest req) {
        long id = user(req).id();
        return jdbc.queryForMap(
                "SELECT (SELECT count(*) FROM deployments WHERE user_id = ?) AS deployments, "
                        + "(SELECT count(*) FROM incidents WHERE user_id = ?) AS incidents, "
                        + "(SELECT count(*) FROM incidents WHERE user_id = ? AND outcome = 'RECOVERED') AS recovered, "
                        + "COALESCE((SELECT ROUND(AVG(recovery_seconds)) FROM incidents "
                        + "WHERE user_id = ? AND outcome = 'RECOVERED'), 0) AS avg_recovery_seconds",
                id, id, id, id);
    }

    /** Start from scratch: wipes the running state and deploys the baseline release for this user. */
    @PostMapping("/environment/start")
    public ResponseEntity<Map<String, Object>> start(HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        AuthService.User u = user(req);
        if (deployment.isDeployed() && !Objects.equals(deployment.ownerId(), u.id()) && deployment.ownerRecentlyActive()) {
            body.put("error", "Another user is running the demo right now. Try again when they stop it, or after 10 minutes without activity.");
            return ResponseEntity.status(409).body(body);
        }
        try {
            monitoring.reset();
            recovery.reset();
            deployment.startEnvironment(u.id());
            body.put("message", "Started from scratch with " + cfg.baselineVersion());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @PostMapping("/environment/stop")
    public ResponseEntity<Map<String, Object>> stop(HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!isMine(user(req))) {
            body.put("error", "Nothing of yours is running.");
            return ResponseEntity.status(409).body(body);
        }
        deployment.stopEnvironment();
        monitoring.reset();
        recovery.reset();
        body.put("message", "Stopped. Status is back to zero.");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/deploy/{version}")
    public ResponseEntity<Map<String, Object>> deploy(@PathVariable String version, HttpServletRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (!DockerService.isValidVersion(version)) {
            body.put("error", "Version must look like v1.0");
            return ResponseEntity.badRequest().body(body);
        }
        if (!isMine(user(req))) {
            body.put("error", "Start monitoring first.");
            return ResponseEntity.status(409).body(body);
        }
        try {
            deployment.deploy(version, "manual deployment from dashboard");
            body.put("message", "Deployed " + version);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @GetMapping("/incidents")
    public List<Map<String, Object>> incidents(HttpServletRequest req) {
        return jdbc.queryForList("SELECT * FROM incidents WHERE user_id = ? ORDER BY id DESC LIMIT 20", user(req).id());
    }

    @GetMapping("/deployments")
    public List<Map<String, Object>> deployments(HttpServletRequest req) {
        return jdbc.queryForList("SELECT * FROM deployments WHERE user_id = ? ORDER BY id DESC LIMIT 20", user(req).id());
    }
}
