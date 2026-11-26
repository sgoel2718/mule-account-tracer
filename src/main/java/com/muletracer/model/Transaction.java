package com.muletracer.model;

import java.util.Objects;

/**
 * An immutable money movement between two accounts.
 *
 * @param id        unique transaction reference (UTR / RRN)
 * @param from      debited account id
 * @param to        credited account id (for cash-out channels, a pseudo sink id such as {@code CASH:ATM-DEL-114})
 * @param amount    amount in paise (1 rupee = 100 paise) so all arithmetic is exact
 * @param timestamp event time in epoch seconds
 * @param channel   payment rail
 */
public record Transaction(String id, String from, String to, long amount, long timestamp, Channel channel) {

    public Transaction {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(channel, "channel");
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive: " + amount);
        }
        if (from.equals(to)) {
            throw new IllegalArgumentException("self-transfer not allowed: " + id);
        }
    }
}
