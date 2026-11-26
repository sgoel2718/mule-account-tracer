package com.muletracer.trace;

import com.muletracer.model.Transaction;

/**
 * A transaction that carried part of the victim's money.
 *
 * @param txn           the underlying transaction
 * @param taintedAmount how much of {@code txn.amount()} was the victim's money (paise)
 * @param hop           layer number: 1 = the victim's own transfer into the first mule
 */
public record TaintedEdge(Transaction txn, long taintedAmount, int hop) {
}
