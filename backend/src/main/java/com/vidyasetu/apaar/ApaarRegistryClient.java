package com.vidyasetu.apaar;

import java.time.LocalDate;

public interface ApaarRegistryClient {
    enum Status { FOUND, NOT_FOUND, UNAVAILABLE }

    record ApaarLookupResult(Status status, String fullName, LocalDate dob, String gender, String registryStatus) {
        public static ApaarLookupResult found(String fullName, LocalDate dob, String gender, String regStatus) {
            return new ApaarLookupResult(Status.FOUND, fullName, dob, gender, regStatus);
        }
        public static ApaarLookupResult notFound() {
            return new ApaarLookupResult(Status.NOT_FOUND, null, null, null, null);
        }
        public static ApaarLookupResult unavailable() {
            return new ApaarLookupResult(Status.UNAVAILABLE, null, null, null, null);
        }
    }

    ApaarLookupResult lookup(String apaarId);
}
