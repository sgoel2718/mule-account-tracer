package com.muletracer.trace;

/**
 * Where an account stands in a trace.
 *
 * @param accountId        account id
 * @param hop              shortest layer distance from the victim (1 = received directly)
 * @param received         total victim money that flowed into this account (paise)
 * @param held             victim money still sitting here at report time = what a freeze recovers
 * @param cashedOut        victim money this account withdrew as cash / crypto
 * @param firstTaintedAt   epoch seconds when victim money first landed here
 */
public record AccountTaint(String accountId, int hop, long received, long held, long cashedOut, long firstTaintedAt) {
}
