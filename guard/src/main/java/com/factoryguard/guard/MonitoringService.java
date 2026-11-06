package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Every few seconds: measure the demo app and judge it against the policy.
 * Healthy means: health page is UP AND throughput is at or above the threshold.
 * A rollback only happens after several bad checks IN A ROW (a sustained drop, not a blip).
 */
@Service
public class MonitoringService {

    private static final Logger log = LoggerFactory.getLogger(MonitoringService.class);

    private final Prober prober;
    private final DeploymentService deployment;
    private final RecoveryService recovery;
    private final GuardConfig cfg;
    private final CloudWatchPublisher cloudWatch;

    private final LinkedList<Sample> samples = new LinkedList<>();
    private volatile String state = "NOT_STARTED";
    private volatile String lastAction = "";
    private volatile int failStreak = 0;

    public MonitoringService(Prober prober, DeploymentService deployment, RecoveryService recovery, GuardConfig cfg,
                             CloudWatchPublisher cloudWatch) {
        this.prober = prober;
        this.deployment = deployment;
        this.recovery = recovery;
        this.cfg = cfg;
        this.cloudWatch = cloudWatch;
    }

    @Scheduled(fixedDelayString = "#{${guard.check-interval-seconds} * 1000}", initialDelay = 5000)
    public void check() {
        if (!deployment.isDeployed()) {
            state = "NOT_STARTED";
            return;
        }
        Sample s = prober.probe(deployment.currentVersion());
        cloudWatch.publishSample(s);
        synchronized (samples) {
            samples.addLast(s);
            while (samples.size() > 120) {
                samples.removeFirst();
            }
        }

        // Warm-up: the normal grace period, extended (up to 3x) while the app has not come up at all yet.
        long since = deployment.secondsSinceDeploy();
        boolean starting = since < cfg.warmupSeconds() || (!s.healthUp() && since < cfg.warmupSeconds() * 3L);
        if (starting) {
            state = "WARMING_UP";
            failStreak = 0;
            return;
        }

        boolean ok = s.healthUp() && s.eventsPerMinute() >= cfg.threshold();
        if (ok) {
            failStreak = 0;
            deployment.markHealthy();
            state = "HEALTHY";
            return;
        }

        failStreak++;
        state = "DEGRADED (" + failStreak + "/" + cfg.failuresBeforeRollback() + ")";
        log.warn("Bad check {}/{}: health={}, events/min={}", failStreak, cfg.failuresBeforeRollback(),
                s.healthUp(), s.eventsPerMinute());

        if (failStreak >= cfg.failuresBeforeRollback()) {
            failStreak = 0;
            state = "RECOVERING";
            String reason = !s.healthUp()
                    ? "health check failing"
                    : String.format("throughput %.1f events/min stayed below threshold %.0f", s.eventsPerMinute(), cfg.threshold());
            lastAction = recovery.handleFailure(s, reason);
            state = "VERIFYING";
        }
    }

    /** Back to zero: forget all measurements (used when a user starts or stops the environment). */
    public void reset() {
        synchronized (samples) {
            samples.clear();
        }
        failStreak = 0;
        state = "NOT_STARTED";
        lastAction = "";
    }

    public int failStreak() { return failStreak; }
    public String state() { return state; }
    public String lastAction() { return lastAction; }

    public List<Sample> recentSamples() {
        synchronized (samples) {
            return new ArrayList<>(samples);
        }
    }
}
