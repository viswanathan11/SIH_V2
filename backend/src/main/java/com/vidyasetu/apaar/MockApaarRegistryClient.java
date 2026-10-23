package com.vidyasetu.apaar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Random;

@Component
@ConditionalOnProperty(name = "apaar.provider", havingValue = "mock", matchIfMissing = true)
public class MockApaarRegistryClient implements ApaarRegistryClient {
    private static final Logger log = LoggerFactory.getLogger(MockApaarRegistryClient.class);

    private final JdbcTemplate jdbc;
    private final boolean chaosEnabled;
    private final int latencyMs;
    private final Random random = new Random();

    public MockApaarRegistryClient(JdbcTemplate jdbc,
                                  @Value("${apaar.mock.chaos:false}") boolean chaosEnabled,
                                  @Value("${apaar.mock.latency-ms:0}") int latencyMs) {
        this.jdbc = jdbc;
        this.chaosEnabled = chaosEnabled;
        this.latencyMs = latencyMs;
    }

    @Override
    public ApaarLookupResult lookup(String apaarId) {
        if (latencyMs > 0) {
            try { Thread.sleep(latencyMs); } catch (InterruptedException ignored) {}
        }

        // Optional chaos: 1% chance of unavailable to test retry path
        if (chaosEnabled && random.nextInt(100) < 1) {
            log.info("Simulating APAAR registry outage for {}", apaarId);
            return ApaarLookupResult.unavailable();
        }

        return jdbc.query("SELECT full_name, dob, gender, status FROM apaar_registry_mock WHERE apaar_id = ?",
                (rs, rowNum) -> {
                    LocalDate dob = rs.getDate("dob") != null ? rs.getDate("dob").toLocalDate() : null;
                    return ApaarLookupResult.found(rs.getString("full_name"), dob, rs.getString("gender"), rs.getString("status"));
                }, apaarId).stream().findFirst().orElseGet(ApaarLookupResult::notFound);
    }
}
