package com.factoryguard.guard;

/** One measurement of the demo app. */
public record Sample(long timestamp, String version, boolean healthUp, double eventsPerMinute) {
}
