package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * The heart of FactoryGuard: when a release is judged unhealthy, record the incident, alert the team,
 * restore the last known-good version and verify that it works.
 *
 * Every incident stores its own evidence: the detected metric, the reason, the failed release, the rollback
 * target, how long detection took, how long recovery took, and the final verification result.
 * The rollback counter lives in the database, so it survives a restart of FactoryGuard.
 */
@Service
public class RecoveryService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryService.class);

    private final DeploymentService deployment;
    private final Prober prober;
    private final NotificationService notifier;
    private final JdbcTemplate jdbc;
    private final GuardConfig cfg;
    private final CloudWatchPublisher cloudWatch;

    public RecoveryService(DeploymentService deployment, Prober prober, NotificationService notifier,
                           JdbcTemplate jdbc, GuardConfig cfg, CloudWatchPublisher cloudWatch) {
        this.deployment = deployment;
        this.prober = prober;
        this.notifier = notifier;
        this.jdbc = jdbc;
        this.cfg = cfg;
        this.cloudWatch = cloudWatch;
    }

    /** Returns a short text describing what happened (shown on the dashboard). */
    public String handleFailure(Sample sample, String reason) {
        String bad = deployment.currentVersion();
        String good = deployment.lastKnownGood();
        long badDeploymentId = deployment.currentDeploymentId();
        boolean hasTarget = !bad.equals(good) && !"none".equals(good);

        // The only version we have is the broken one: record it once, not every few seconds.
        if (!hasTarget && recentNoTargetIncident(bad)) {
            return "still unhealthy, no rollback target";
        }

        String metrics = String.format(Locale.US, "events/min=%.1f, health=%s, threshold=%.0f",
                sample.eventsPerMinute(), sample.healthUp() ? "UP" : "DOWN", cfg.threshold());

        long incidentId = jdbc.queryForObject(
                "INSERT INTO incidents (version, reason, metrics, action, user_id, rollback_target, threshold, "
                        + "detected_events_per_minute, detected_health_up, detection_seconds) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + "(SELECT ROUND(EXTRACT(EPOCH FROM (now() - created_at)))::int FROM deployments WHERE id = ?)) "
                        + "RETURNING id",
                Long.class, bad, reason, metrics, "evaluating rollback to " + good, deployment.ownerId(),
                hasTarget ? good : null, cfg.threshold(), sample.eventsPerMinute(), sample.healthUp(), badDeploymentId);
        Instant detected = Instant.now();
        cloudWatch.publishIncident();
        notifier.alert("Release " + bad + " is unhealthy: " + reason + " (" + metrics + ")");

        if (recentRollbackCount() >= cfg.maxRollbacksPer10Min()) {
            jdbc.update("UPDATE incidents SET outcome = 'ROLLBACK_LIMIT_REACHED', action = ?, verification = ?, resolved_at = now() WHERE id = ?",
                    "automatic rollback skipped: limit reached, human needed", "not attempted (rollback limit reached)", incidentId);
            notifier.alert("Rollback limit reached. A human needs to look at this.");
            return "rollback limit reached";
        }
        if (!hasTarget) {
            jdbc.update("UPDATE incidents SET outcome = 'NO_ROLLBACK_TARGET', action = ?, verification = ?, resolved_at = now() WHERE id = ?",
                    "no earlier healthy version to restore", "not attempted (no known-good version other than this one)", incidentId);
            return "no rollback target";
        }

        try {
            jdbc.update("INSERT INTO rollback_events (user_id) VALUES (?)", deployment.ownerId());
            jdbc.update("UPDATE incidents SET action = ? WHERE id = ?", "rolling back to " + good, incidentId);
            deployment.deploy(good, "automatic rollback from " + bad);
            deployment.markRolledBack(badDeploymentId);

            // Verify: give the restored version time to warm up, then check it. A slow start gets a few more tries.
            Thread.sleep(cfg.warmupSeconds() * 1000L);
            Instant giveUp = detected.plusSeconds(cfg.warmupSeconds() * 3L);
            Sample after = prober.probe(good);
            boolean ok = isHealthy(after);
            while (!ok && Instant.now().isBefore(giveUp)) {
                Thread.sleep(5000);
                after = prober.probe(good);
                ok = isHealthy(after);
            }
            int seconds = (int) Duration.between(detected, Instant.now()).getSeconds();
            String verification = String.format(Locale.US, "health=%s, events/min=%.1f (threshold %.0f): %s",
                    after.healthUp() ? "UP" : "DOWN", after.eventsPerMinute(), cfg.threshold(), ok ? "PASSED" : "FAILED");

            jdbc.update("UPDATE incidents SET outcome = ?, action = ?, recovery_seconds = ?, resolved_at = now(), "
                            + "verified_events_per_minute = ?, verified_health_up = ?, verification = ? WHERE id = ?",
                    ok ? "RECOVERED" : "RECOVERY_FAILED",
                    "rolled back from " + bad + " to " + good,
                    seconds, after.eventsPerMinute(), after.healthUp(), verification, incidentId);
            notifier.alert(ok
                    ? "Recovered: " + good + " restored in " + seconds + " seconds."
                    : "Rollback to " + good + " did not restore health. Human needed.");
            return ok ? "recovered in " + seconds + "s" : "recovery failed";
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return "interrupted";
        } catch (Exception e) {
            log.error("Rollback failed", e);
            jdbc.update("UPDATE incidents SET outcome = 'RECOVERY_FAILED', action = ?, verification = ?, resolved_at = now() WHERE id = ?",
                    "rollback error: " + e.getMessage(), "not verified (rollback error)", incidentId);
            notifier.alert("Rollback failed: " + e.getMessage());
            return "rollback error";
        }
    }

    private boolean isHealthy(Sample s) {
        return s.healthUp() && s.eventsPerMinute() >= cfg.threshold();
    }

    private boolean recentNoTargetIncident(String version) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM incidents WHERE version = ? AND outcome = 'NO_ROLLBACK_TARGET' "
                        + "AND detected_at > now() - interval '5 minutes'", Integer.class, version);
        return n != null && n > 0;
    }

    /** How many automatic rollbacks happened in the last 10 minutes (read from the database). */
    public int recentRollbackCount() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM rollback_events WHERE at > now() - interval '10 minutes'", Integer.class);
        return n == null ? 0 : n;
    }

    /** Forget past rollbacks (used when a user starts or stops from scratch). */
    public void reset() {
        jdbc.update("DELETE FROM rollback_events");
    }
}
