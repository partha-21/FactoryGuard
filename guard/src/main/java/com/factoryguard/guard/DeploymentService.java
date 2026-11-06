package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Knows which version is running, who started it, and which version was the last "known good" one.
 * A version becomes known-good only after it passes several health checks in a row.
 * Nothing runs until a signed-in user starts monitoring ("from scratch").
 *
 * The recovery context (running version, known-good version, owner, deployment time) is saved to the
 * database on every change, so restarting FactoryGuard does not lose it.
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    /** Another user may only take over the demo after the owner has been inactive this long. */
    private static final Duration OWNER_IDLE_LIMIT = Duration.ofMinutes(10);

    private final DockerService docker;
    private final JdbcTemplate jdbc;
    private final GuardConfig cfg;

    private volatile Long ownerId = null;
    private volatile String currentVersion = "none";
    private volatile String lastKnownGood = "none";
    private volatile Instant deployedAt = null;
    private volatile long currentDeploymentId = -1;
    private volatile int healthyStreak = 0;
    private volatile Instant lastOwnerActivity = Instant.now();

    public DeploymentService(DockerService docker, JdbcTemplate jdbc, GuardConfig cfg) {
        this.docker = docker;
        this.jdbc = jdbc;
        this.cfg = cfg;
    }

    /** Start from a clean slate for this user: stop anything running, then deploy the baseline release. */
    public synchronized void startEnvironment(long userId) throws IOException, InterruptedException {
        docker.stopApp();
        ownerId = userId;
        currentVersion = "none";
        lastKnownGood = "none";
        deployedAt = null;
        currentDeploymentId = -1;
        healthyStreak = 0;
        lastOwnerActivity = Instant.now();
        try {
            deploy(cfg.baselineVersion(), "initial deployment (started from scratch)");
            lastKnownGood = cfg.baselineVersion();
            jdbc.update("UPDATE deployments SET status = 'HEALTHY' WHERE id = ?", currentDeploymentId);
            persistState();
        } catch (IOException | InterruptedException | RuntimeException e) {
            ownerId = null;
            deployedAt = null;
            clearPersistedState();
            throw e;
        }
    }

    public synchronized void stopEnvironment() {
        docker.stopApp();
        ownerId = null;
        currentVersion = "none";
        lastKnownGood = "none";
        deployedAt = null;
        currentDeploymentId = -1;
        healthyStreak = 0;
        clearPersistedState();
        log.info("Environment stopped");
    }

    public synchronized void deploy(String version, String note) throws IOException, InterruptedException {
        if (!DockerService.isValidVersion(version)) {
            throw new IOException("Version must look like v1.0");
        }
        if (!docker.imageExists(version)) {
            throw new IOException("Image for " + version + " was not found. Run the start script again to build it.");
        }
        docker.startVersion(version);
        currentDeploymentId = jdbc.queryForObject(
                "INSERT INTO deployments (version, status, note, user_id) VALUES (?, 'DEPLOYED', ?, ?) RETURNING id",
                Long.class, version, note, ownerId);
        currentVersion = version;
        deployedAt = Instant.now();
        healthyStreak = 0;
        persistState();
        log.info("Deployed {} ({})", version, note);
    }

    /** Called after every successful check. Promotes the version to known-good when it has earned it. */
    public void markHealthy() {
        healthyStreak++;
        if (healthyStreak >= cfg.healthyChecksToPromote() && !currentVersion.equals(lastKnownGood)) {
            lastKnownGood = currentVersion;
            jdbc.update("UPDATE deployments SET status = 'HEALTHY' WHERE id = ?", currentDeploymentId);
            persistState();
            log.info("{} is now the last known-good version", currentVersion);
        }
    }

    public void markRolledBack(long deploymentId) {
        jdbc.update("UPDATE deployments SET status = 'ROLLED_BACK' WHERE id = ?", deploymentId);
    }

    // ---------- persistence ----------

    private void persistState() {
        Instant t = deployedAt;
        if (t == null || ownerId == null) {
            return;
        }
        jdbc.update("INSERT INTO guard_state (id, owner_id, current_version, last_known_good, deployed_at, current_deployment_id) "
                        + "VALUES (1, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT (id) DO UPDATE SET owner_id = EXCLUDED.owner_id, current_version = EXCLUDED.current_version, "
                        + "last_known_good = EXCLUDED.last_known_good, deployed_at = EXCLUDED.deployed_at, "
                        + "current_deployment_id = EXCLUDED.current_deployment_id",
                ownerId, currentVersion, lastKnownGood, Timestamp.from(t), currentDeploymentId);
    }

    private void clearPersistedState() {
        jdbc.update("DELETE FROM guard_state");
    }

    /**
     * After a FactoryGuard restart: pick up where we left off, but only if the demo app container is really
     * still running the version we saved. Otherwise start from a clean slate. Returns true if state was restored.
     */
    public synchronized boolean restoreState() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT owner_id, current_version, last_known_good, EXTRACT(EPOCH FROM deployed_at) AS deployed_epoch, "
                        + "current_deployment_id FROM guard_state WHERE id = 1");
        if (rows.isEmpty()) {
            return false;
        }
        Map<String, Object> row = rows.get(0);
        String savedVersion = (String) row.get("current_version");
        Optional<String> running = docker.runningVersion();
        if (running.isEmpty() || !running.get().equals(savedVersion)) {
            clearPersistedState();
            return false;
        }
        ownerId = ((Number) row.get("owner_id")).longValue();
        currentVersion = savedVersion;
        lastKnownGood = (String) row.get("last_known_good");
        deployedAt = Instant.ofEpochMilli((long) (((Number) row.get("deployed_epoch")).doubleValue() * 1000));
        currentDeploymentId = ((Number) row.get("current_deployment_id")).longValue();
        healthyStreak = 0;
        lastOwnerActivity = Instant.now();
        log.info("Restored state after restart: running {}, known-good {}", currentVersion, lastKnownGood);
        return true;
    }

    // ---------- who is using the demo ----------

    public void markOwnerActive() {
        lastOwnerActivity = Instant.now();
    }

    public boolean ownerRecentlyActive() {
        return Duration.between(lastOwnerActivity, Instant.now()).compareTo(OWNER_IDLE_LIMIT) < 0;
    }

    public Long ownerId() { return ownerId; }
    public String currentVersion() { return currentVersion; }
    public String lastKnownGood() { return lastKnownGood; }
    public long currentDeploymentId() { return currentDeploymentId; }
    public boolean isDeployed() { return deployedAt != null; }

    public long secondsSinceDeploy() {
        Instant t = deployedAt;
        return t == null ? 0 : Duration.between(t, Instant.now()).getSeconds();
    }
}
