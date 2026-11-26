package com.muletracer.trace;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Output of a {@link TaintTracer} run.
 *
 * <p>Invariant (money is conserved): {@code fraudTotal == cashedOut + freezable + beyondHopLimit}.</p>
 */
public final class TraceResult {

    private final Set<String> victims;
    private final long reportTime;
    private final long horizon;
    private final long fraudTotal;
    private final long cashedOut;
    private final long freezable;
    private final long beyondHopLimit;
    private final Map<String, AccountTaint> accounts;
    private final List<TaintedEdge> edges;
    private final long eventsProcessed;

    TraceResult(Set<String> victims, long reportTime, long horizon, long fraudTotal, long cashedOut,
                long freezable, long beyondHopLimit, Map<String, AccountTaint> accounts,
                List<TaintedEdge> edges, long eventsProcessed) {
        this.victims = Collections.unmodifiableSet(victims);
        this.reportTime = reportTime;
        this.horizon = horizon;
        this.fraudTotal = fraudTotal;
        this.cashedOut = cashedOut;
        this.freezable = freezable;
        this.beyondHopLimit = beyondHopLimit;
        this.accounts = Collections.unmodifiableMap(accounts);
        this.edges = Collections.unmodifiableList(edges);
        this.eventsProcessed = eventsProcessed;
    }

    public Set<String> victims() { return victims; }
    public long reportTime() { return reportTime; }
    public long horizon() { return horizon; }
    public long fraudTotal() { return fraudTotal; }
    public long cashedOut() { return cashedOut; }
    public long freezable() { return freezable; }
    public long beyondHopLimit() { return beyondHopLimit; }
    public Map<String, AccountTaint> accounts() { return accounts; }
    public List<TaintedEdge> edges() { return edges; }
    public long eventsProcessed() { return eventsProcessed; }

    /** Share of the stolen money that is still in the banking system and can be frozen. */
    public double recoveryRate() {
        return fraudTotal == 0 ? 0.0 : (double) freezable / fraudTotal;
    }

    public int maxHopReached() {
        int max = 0;
        for (TaintedEdge e : edges) {
            max = Math.max(max, e.hop());
        }
        return max;
    }
}
