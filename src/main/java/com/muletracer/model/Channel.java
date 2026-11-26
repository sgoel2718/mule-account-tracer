package com.muletracer.model;

/**
 * Payment rail a transaction moved on. Cash-out channels are where traced money
 * leaves the banking system and can no longer be frozen.
 */
public enum Channel {
    UPI(false),
    IMPS(false),
    NEFT(false),
    RTGS(false),
    ATM_WITHDRAWAL(true),
    CRYPTO_P2P(true),
    CHEQUE_CASH(true);

    private final boolean cashOut;

    Channel(boolean cashOut) {
        this.cashOut = cashOut;
    }

    /** True when money on this channel exits the banking system (unrecoverable by a freeze). */
    public boolean isCashOut() {
        return cashOut;
    }
}
