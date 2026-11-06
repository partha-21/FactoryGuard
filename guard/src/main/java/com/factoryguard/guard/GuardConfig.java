package com.factoryguard.guard;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** All FactoryGuard settings in one place (values come from application.properties / .env). */
@ConfigurationProperties("guard")
public record GuardConfig(
        @DefaultValue("demo-app") String containerName,
        @DefaultValue("factoryguard_net") String network,
        @DefaultValue("factoryguard/demo-app") String imageName,
        @DefaultValue("8081") int publishedPort,
        @DefaultValue("mosquitto") String mqttHost,
        @DefaultValue("8080") int demoPort,
        @DefaultValue("v1.0") String baselineVersion,
        @DefaultValue("60") double threshold,
        @DefaultValue("5") int checkIntervalSeconds,
        @DefaultValue("25") int warmupSeconds,
        @DefaultValue("3") int failuresBeforeRollback,
        @DefaultValue("6") int healthyChecksToPromote,
        @DefaultValue("2") int maxRollbacksPer10Min,
        @DefaultValue("") String webhookUrl) {
}
