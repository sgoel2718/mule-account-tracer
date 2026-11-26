package com.muletracer.trace;

import com.muletracer.graph.TemporalGraph;
import com.muletracer.model.Account;
import com.muletracer.model.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Follows a victim's money forward through the transaction graph in time order.
 *
 * <h2>Algorithm: time-respecting taint propagation (event-driven k-way merge)</h2>
 * <ol>
 *   <li>Every fraudulent transfer is a <i>seed event</i>: it credits "tainted" balance to the first mule.</li>
 *   <li>A min-heap orders events by timestamp. Each tainted account has at most <b>one</b> cursor in the
 *       heap pointing at its next debit, so the heap is a k-way merge over the debit lists of tainted
 *       accounts. We never scan debits of untouched accounts.</li>
 *   <li>When a debit fires, it carries {@code min(amount, taintedBalance)} onward (tainted-first rule,
 *       the conservative assumption investigators use so no stolen rupee is missed).</li>
 *   <li>Debits on a cash-out channel (ATM, crypto P2P) move money out of reach; everything still
 *       sitting in accounts at report time is <b>freezable</b>.</li>
 * </ol>
 *
 * <p>Time-respecting means money can only follow a debit that happens after it arrived, which is what
 * separates this from a plain BFS that would wrongly blame every account the mule ever paid.</p>
 *
 * <p>Complexity: O((S + E') log K) where S = seeds, E' = debits of tainted accounts inside the window,
 * K = number of simultaneously tainted accounts. Memory O(K + E').</p>
 */
public final class TaintTracer {

    private final TemporalGraph graph;
    private final TraceConfig config;

    public TaintTracer(TemporalGraph graph, TraceConfig config) {
        this.graph = graph;
        this.config = config;
    }

    /** Heap entry: either a seed (victim's transfer) or a cursor on a tainted account's next debit. */
    private record Event(Transaction txn, String account, int index, boolean seed) {
    }

    private static final Comparator<Event> ORDER = Comparator
            .comparingLong((Event e) -> e.txn().timestamp())
            .thenComparing(e -> e.seed() ? 0 : 1) // credits land before same-second debits
            .thenComparing(e -> e.txn().id());

    /**
     * @param fraudTxns  the victim-reported transfers (UTRs from the complaint)
     * @param reportTime epoch seconds when the trace runs; transactions after it have not happened yet
     */
    public TraceResult trace(List<Transaction> fraudTxns, long reportTime) {
        if (fraudTxns.isEmpty()) {
            throw new IllegalArgumentException("at least one fraudulent transaction is required");
        }
        long start = Long.MAX_VALUE;
        for (Transaction t : fraudTxns) {
            start = Math.min(start, t.timestamp());
        }
        long horizon = Math.min(reportTime, start + config.windowSeconds());

        State s = new State(horizon);
        for (Transaction t : fraudTxns) {
            if (t.timestamp() <= horizon) {
                s.heap.add(new Event(t, t.to(), -1, true));
                s.victims.add(t.from());
            }
        }

        while (!s.heap.isEmpty()) {
            Event e = s.heap.poll();
            s.events++;
            Transaction t = e.txn();
            if (e.seed()) {
                s.fraudTotal += t.amount();
                s.edges.add(new TaintedEdge(t, t.amount(), 1));
                s.credit(t.to(), t.amount(), 1, t.timestamp());
                continue;
            }
            String a = e.account();
            s.lastProcessed.put(a, e.index());
            long balance = s.balance.getOrDefault(a, 0L);
            if (balance > 0) {
                long moved = Math.min(balance, t.amount());
                s.balance.put(a, balance - moved);
                int hop = s.hop.get(a) + 1;
                s.edges.add(new TaintedEdge(t, moved, hop));
                Account dest = graph.account(t.to());
                if (t.channel().isCashOut() || (dest != null && dest.isCashSink())) {
                    s.cashedOut += moved;
                    s.cashedOutBy.merge(a, moved, Long::sum);
                } else if (hop > config.maxHops()) {
                    s.beyondHopLimit += moved;
                } else if (!s.victims.contains(t.to())) {
                    s.credit(t.to(), moved, hop, t.timestamp());
                } else {
                    // money sent back to the victim is effectively recovered; count it as freezable at source
                    s.returnedToVictim += moved;
                }
            }
            s.advance(a, e.index() + 1);
        }

        long freezable = s.returnedToVictim;
        Map<String, AccountTaint> accounts = new HashMap<>();
        for (Map.Entry<String, Long> en : s.received.entrySet()) {
            String id = en.getKey();
            long held = s.balance.getOrDefault(id, 0L);
            freezable += held;
            accounts.put(id, new AccountTaint(id, s.hop.get(id), en.getValue(), held,
                    s.cashedOutBy.getOrDefault(id, 0L), s.firstTaint.get(id)));
        }
        return new TraceResult(s.victims, reportTime, horizon, s.fraudTotal, s.cashedOut, freezable,
                s.beyondHopLimit, accounts, s.edges, s.events);
    }

    /** Mutable per-trace state, kept off the tracer so one tracer can serve concurrent traces. */
    private final class State {
        final long horizon;
        final PriorityQueue<Event> heap = new PriorityQueue<>(ORDER);
        final Map<String, Long> balance = new HashMap<>();
        final Map<String, Long> received = new HashMap<>();
        final Map<String, Integer> hop = new HashMap<>();
        final Map<String, Long> firstTaint = new HashMap<>();
        final Map<String, Long> cashedOutBy = new HashMap<>();
        final Map<String, Integer> lastProcessed = new HashMap<>();
        final Set<String> active = new HashSet<>();
        final Set<String> victims = new HashSet<>();
        final List<TaintedEdge> edges = new ArrayList<>();
        long fraudTotal;
        long cashedOut;
        long beyondHopLimit;
        long returnedToVictim;
        long events;

        State(long horizon) {
            this.horizon = horizon;
        }

        void credit(String account, long amount, int hopNo, long ts) {
            balance.merge(account, amount, Long::sum);
            received.merge(account, amount, Long::sum);
            hop.merge(account, hopNo, Math::min);
            firstTaint.putIfAbsent(account, ts);
            if (!active.contains(account)) {
                int from = graph.firstOutgoingAtOrAfter(account, ts);
                int after = lastProcessed.getOrDefault(account, -1) + 1; // never replay a debit
                advance(account, Math.max(from, after));
            }
        }

        /** Puts the account's debit at {@code index} on the heap, or deactivates its cursor. */
        void advance(String account, int index) {
            List<Transaction> out = graph.outgoing(account);
            if (balance.getOrDefault(account, 0L) > 0 && index < out.size()
                    && out.get(index).timestamp() <= horizon) {
                heap.add(new Event(out.get(index), account, index, false));
                active.add(account);
            } else {
                active.remove(account);
            }
        }
    }
}
