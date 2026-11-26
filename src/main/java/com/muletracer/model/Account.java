package com.muletracer.model;

import java.util.Objects;

/**
 * KYC-level metadata a bank holds for an account.
 *
 * @param id        account id
 * @param bank      bank short code (e.g. SBI, HDFC)
 * @param city      home branch city
 * @param ageDays   days since account opening (fresh accounts are a classic mule signal)
 * @param deviceId  fingerprint of the device last used for login
 * @param ipHash    hashed IP / network the account last logged in from
 */
public record Account(String id, String bank, String city, int ageDays, String deviceId, String ipHash) {

    public Account {
        Objects.requireNonNull(id, "id");
        if (ageDays < 0) {
            throw new IllegalArgumentException("ageDays must be >= 0");
        }
    }

    /** Placeholder for accounts we only know from the other side of a transaction. */
    public static Account unknown(String id) {
        return new Account(id, "UNKNOWN", "UNKNOWN", 0, null, null);
    }

    public boolean isCashSink() {
        return id.startsWith("CASH:");
    }
}
