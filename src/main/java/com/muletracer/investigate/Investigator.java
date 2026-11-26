package com.muletracer.investigate;

import com.muletracer.cluster.MuleRing;
import com.muletracer.cluster.MuleRingDetector;
import com.muletracer.cut.ChokepointFinder;
import com.muletracer.graph.TemporalGraph;
import com.muletracer.model.Account;
import com.muletracer.model.Transaction;
import com.muletracer.score.MuleScore;
import com.muletracer.score.MuleScorer;
import com.muletracer.trace.AccountTaint;
import com.muletracer.trace.TaintTracer;
import com.muletracer.trace.TraceConfig;
import com.muletracer.trace.TraceResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;

/**
 * Pipeline for one complaint: trace -> rings -> scores -> chokepoints -> prioritised freeze list.
 *
 * <p>Freeze orders are drained from a max-heap keyed on {@code heldAmount * risk}, so the fraud desk
 * acts first where a freeze recovers the most money with the least chance of hitting an innocent.</p>
 */
public final class Investigator {

    private final TemporalGraph graph;
    private final TraceConfig config;

    private final MuleRingDetector ringDetector;
    private final TaintTracer tracer;

    public Investigator(TemporalGraph graph, TraceConfig config) {
        this.graph = graph;
        this.config = config;
        this.tracer = new TaintTracer(graph, config);
        this.ringDetector = new MuleRingDetector(graph); // built once, then queried per complaint
    }

    public MuleRingDetector ringDetector() {
        return ringDetector;
    }

    public TraceConfig config() {
        return config;
    }

    public Investigation investigate(List<Transaction> fraudTxns, long reportTime) {
        long t0 = System.nanoTime();
        TraceResult trace = tracer.trace(fraudTxns, reportTime);
        List<MuleRing> rings = ringDetector.detect(trace.accounts().keySet());
        Map<String, MuleScore> scores = new MuleScorer(graph).score(trace, rings);
        ChokepointFinder.Result cut = new ChokepointFinder().find(trace);

        Map<String, String> ringOf = new HashMap<>();
        Set<String> watch = new TreeSet<>();
        for (MuleRing r : rings) {
            for (String m : r.members()) {
                ringOf.put(m, r.id());
            }
            if (r.taintedMembers().size() >= 2) {
                watch.addAll(r.dormantMembers());
            }
        }
        Set<String> choke = new HashSet<>(cut.chokepoints());

        PriorityQueue<FreezeOrder> pq = new PriorityQueue<>(Comparator
                .comparingDouble((FreezeOrder o) -> -(o.heldPaise() * (o.score() / 100.0)))
                .thenComparing(o -> o.chokepoint() ? 0 : 1)
                .thenComparing(o -> -o.score())
                .thenComparing(FreezeOrder::accountId));
        for (AccountTaint at : trace.accounts().values()) {
            MuleScore ms = scores.get(at.accountId());
            boolean isChoke = choke.contains(at.accountId());
            String action;
            if (ms.score() >= 70 && at.held() > 0) {
                action = "FREEZE";
            } else if (at.held() > 0) {
                action = "LIEN"; // likely innocent or unsure: hold only the victim's amount, leave the rest usable
            } else if (isChoke && ms.score() >= 40) {
                action = "BLOCK";
            } else {
                continue;
            }
            Account acc = graph.account(at.accountId());
            pq.add(new FreezeOrder(at.accountId(), acc.bank(), acc.city(), at.hop(), at.held(), ms.score(),
                    ms.verdict(), action, isChoke, ringOf.get(at.accountId()), ms.reasons()));
        }
        List<FreezeOrder> orders = new ArrayList<>(pq.size());
        while (!pq.isEmpty()) {
            orders.add(pq.poll());
        }
        return new Investigation(trace, rings, scores, cut, orders, new ArrayList<>(watch), System.nanoTime() - t0);
    }
}
