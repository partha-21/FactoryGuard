package com.factoryguard.guard;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Talks to Docker. This is how a "deployment" and a "rollback" actually happen. */
@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private static final Pattern VERSION = Pattern.compile("^v[0-9]+\\.[0-9]+$");

    private final GuardConfig cfg;

    public DockerService(GuardConfig cfg) {
        this.cfg = cfg;
    }

    public static boolean isValidVersion(String version) {
        return version != null && VERSION.matcher(version).matches();
    }

    public boolean imageExists(String version) {
        try {
            run("image", "inspect", cfg.imageName() + ":" + version);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Replace whatever is running with the chosen version of the demo app. */
    public void startVersion(String version) throws IOException, InterruptedException {
        if (!isValidVersion(version)) {
            throw new IOException("Invalid version: " + version);
        }
        try {
            run("rm", "-f", cfg.containerName());
        } catch (IOException ignored) {
            // nothing to remove the first time
        }
        run("run", "-d",
                "--name", cfg.containerName(),
                "--network", cfg.network(),
                "-p", "127.0.0.1:" + cfg.publishedPort() + ":8080",
                "-e", "MQTT_HOST=" + cfg.mqttHost(),
                cfg.imageName() + ":" + version);
        log.info("Started {}:{}", cfg.imageName(), version);
    }

    /** The version of the demo app container if it is running right now, otherwise empty. */
    public Optional<String> runningVersion() {
        try {
            String out = run("inspect", "-f", "{{.State.Running}}|{{.Config.Image}}", cfg.containerName());
            String[] parts = out.split("\\|", 2);
            String prefix = cfg.imageName() + ":";
            if (parts.length == 2 && "true".equals(parts[0].trim()) && parts[1].trim().startsWith(prefix)) {
                return Optional.of(parts[1].trim().substring(prefix.length()));
            }
        } catch (Exception ignored) {
            // no such container
        }
        return Optional.empty();
    }

    /** Stop and remove the demo app container (used for "stop" and "start from scratch"). */
    public void stopApp() {
        try {
            run("rm", "-f", cfg.containerName());
        } catch (Exception ignored) {
            // nothing was running
        }
    }

    private String run(String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.addAll(Arrays.asList(args));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new IOException("docker command timed out: " + String.join(" ", cmd));
        }
        if (p.exitValue() != 0) {
            throw new IOException("docker " + String.join(" ", args) + " failed: " + output);
        }
        return output;
    }
}
