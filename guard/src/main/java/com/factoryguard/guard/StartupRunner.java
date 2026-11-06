package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * On startup: pick up the saved recovery context if the demo app is still running the saved version,
 * otherwise start clean. Incidents that were still open when FactoryGuard stopped are marked INTERRUPTED.
 */
@Component
public class StartupRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final DockerService docker;
    private final DeploymentService deployment;
    private final JdbcTemplate jdbc;

    public StartupRunner(DockerService docker, DeploymentService deployment, JdbcTemplate jdbc) {
        this.docker = docker;
        this.deployment = deployment;
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            int interrupted = jdbc.update("UPDATE incidents SET outcome = 'INTERRUPTED', resolved_at = now(), "
                    + "verification = 'not verified (FactoryGuard restarted before this finished)' WHERE outcome = 'OPEN'");
            if (interrupted > 0) {
                log.warn("{} incident(s) were still open at restart and are now marked INTERRUPTED", interrupted);
            }
            if (!deployment.restoreState()) {
                docker.stopApp();
                log.info("Nothing to restore; starting with nothing running");
            }
        } catch (Exception e) {
            log.error("Could not restore saved state: {}", e.getMessage());
            docker.stopApp();
        }
    }
}
