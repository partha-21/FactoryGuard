package com.factoryguard.guard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CloudWatchPublisherTest {

    @Test
    void stayedOffWhenDisabled() {
        CloudWatchPublisher p = new CloudWatchPublisher(false, "eu-west-1");
        assertFalse(p.isEnabled());
        assertDoesNotThrow(() -> p.publishSample(new Sample(1L, "v1.0", true, 100.0)));
        assertDoesNotThrow(p::publishIncident);
    }

    @Test
    void staysOffWithoutARegion() {
        assertFalse(new CloudWatchPublisher(true, "").isEnabled());
    }
}
