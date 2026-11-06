package com.factoryguard.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerServiceTest {

    @Test
    void acceptsNormalVersions() {
        assertTrue(DockerService.isValidVersion("v1.0"));
        assertTrue(DockerService.isValidVersion("v1.1"));
        assertTrue(DockerService.isValidVersion("v12.34"));
    }

    @Test
    void rejectsAnythingThatCouldInjectCommands() {
        assertFalse(DockerService.isValidVersion(null));
        assertFalse(DockerService.isValidVersion(""));
        assertFalse(DockerService.isValidVersion("1.0"));
        assertFalse(DockerService.isValidVersion("v1.0; rm -rf /"));
        assertFalse(DockerService.isValidVersion("v1.0 --privileged"));
        assertFalse(DockerService.isValidVersion("latest"));
    }
}
