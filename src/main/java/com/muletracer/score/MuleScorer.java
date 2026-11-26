package com.muletracer.score;

import com.muletracer.cluster.MuleRing;
import com.muletracer.graph.TemporalGraph;
import com.muletracer.model.Account;
import com.muletracer.model.Transaction;
import com.muletracer.trace.AccountTaint;
import com.muletracer.trace.TaintedEdge;
import com.muletracer.trace.TraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scores every account that touched the victim's money, so investigators freeze mules and not the
 * kirana shop a mule happened to pay.
 *
 * <p>Signals (each normalised to 0..1, weights sum to 1):</p>
 * <table>
 *   <tr><td>concentration</td><td>0.20</td><td>share of the account's recent inflow that is victim money</td></tr>
 *   <tr><td>passThrough</td><td>0.20</td><td>money forwarded out / money received (mules keep nothing)</td></tr>
 *   <tr><td>speed</td><td>0.15</td><td>minutes between receiving victim money and the next debit</td></tr>
 *   <tr><td>ring</td><td>0.15</td><td>shares devices / networks with other tainted accounts</td></tr>
 *   <tr><td>newAccount</td><td>0.15</td><td>account opened recently</td></tr>
 *   <tr><td>hub</td><td>0.10</td><td>PageRank on the money-flow graph (aggregator mules)</td></tr>
 *   <tr><td>fanOut</td><td>0.05</td><td>splits money across many accounts</td></tr>
 * </table>
 */
public final class MuleScorer {

    private static final long DAY = 24 * 3600L;

    private final TemporalGraph graph;

    public MuleScorer(TemporalGraph graph) {
        this.graph = graph;
    }

    public Map<String, MuleScore> score(TraceResult trace, List<MuleRing> rings) {
        Map<String, Integer> ringSize = new HashMap<>();
        for (MuleRing r : rings) {
            for (String m : r.members()) {
                ringSize.put(m, r.taintedMembers().size());
            }
        }

        PageRank pr = new PageRank();
        Map<String, Set<String>> taintedFanOut = new HashMap<>();
        for (TaintedEdge e : trace.edges()) {
            Transaction t = e.txn();
            pr.addEdge(t.from(), t.to(), e.taintedAmount());
            taintedFanOut.computeIfAbsent(t.from(), k -> new HashSet<>()).add(t.to());
        }
        Map<String, Double> rank = pr.compute(0.85, 40);

        Map<String, MuleScore> out = new LinkedHashMap<>();
        for (AccountTaint at : trace.accounts().values()) {
            out.put(at.accountId(), scoreOne(at, trace.reportTime(), ringSize.getOrDefault(at.accountId(), 0),
                    rank.getOrDefault(at.accountId(), 0.0),
                    taintedFanOut.getOrDefault(at.accountId(), Set.of()).size()));
        }
        return out;
    }

    private MuleScore scoreOne(AccountTaint at, long reportTime, int taintedRingMates, double hubRank, int fanOut) {
        String id = at.accountId();
        Account acc = graph.account(id);
        List<String> reasons = new ArrayList<>();

        long windowStart = at.firstTaintedAt() - DAY;
        long inflow = 0;
        for (Transaction t : graph.incoming(id)) {
            if (t.timestamp() >= windowStart && t.timestamp() <= reportTime) {
                inflow += t.amount();
            }
        }
        long outflow = 0;
        long firstDebitAfterTaint = -1;
        for (Transaction t : graph.outgoing(id)) {
            if (t.timestamp() >= windowStart && t.timestamp() <= reportTime) {
                outflow += t.amount();
            }
            if (firstDebitAfterTaint < 0 && t.timestamp() >= at.firstTaintedAt() && t.timestamp() <= reportTime) {
                firstDebitAfterTaint = t.timestamp();
            }
        }

        double concentration = inflow == 0 ? 0 : Math.min(1.0, (double) at.received() / inflow);
        double passThrough = inflow == 0 ? 0 : Math.min(1.0, (double) outflow / inflow);
        double speed = 0;
        if (firstDebitAfterTaint >= 0) {
            double minutes = (firstDebitAfterTaint - at.firstTaintedAt()) / 60.0;
            speed = minutes <= 10 ? 1.0 : minutes >= 240 ? 0.0 : 1.0 - (minutes - 10) / 230.0;
            if (minutes <= 15) {
                reasons.add(String.format("forwarded money %.0f min after receiving it", minutes));
            }
        }
        double ring = taintedRingMates >= 3 ? 1.0 : taintedRingMates == 2 ? 0.6 : 0.0;
        int age = acc == null ? 0 : acc.ageDays();
        double newAccount = age <= 30 ? 1.0 : age >= 365 ? 0.0 : 1.0 - (age - 30) / 335.0;
        double fan = Math.min(1.0, fanOut / 4.0);

        if (concentration >= 0.6) {
            reasons.add(String.format("%.0f%% of recent inflow is victim money", concentration * 100));
        }
        if (passThrough >= 0.8) {
            reasons.add(String.format("pass-through account: forwarded %.0f%% of inflow", passThrough * 100));
        }
        if (ring > 0) {
            reasons.add("shares device/network with " + (taintedRingMates - 1) + " other tainted account(s)");
        }
        if (age <= 90) {
            reasons.add("account only " + age + " days old");
        }
        if (hubRank >= 0.5) {
            reasons.add("aggregation hub in the money-flow graph");
        }
        if (fanOut >= 3) {
            reasons.add("split money across " + fanOut + " accounts");
        }

        // If the account has not debited anything since the victim money landed, pass-through and speed
        // are unknown rather than "innocent": score on the signals we can observe and renormalise.
        boolean behaviourObserved = firstDebitAfterTaint >= 0;
        double s;
        if (behaviourObserved) {
            s = 0.20 * concentration + 0.20 * passThrough + 0.15 * speed + 0.15 * ring
                    + 0.15 * newAccount + 0.10 * hubRank + 0.05 * fan;
        } else {
            s = (0.20 * concentration + 0.15 * ring + 0.15 * newAccount + 0.10 * hubRank + 0.05 * fan) / 0.65;
            if (concentration >= 0.6) {
                reasons.add("victim money not moved yet: freeze now before it is");
            }
        }
        Map<String, Double> f = new LinkedHashMap<>();
        f.put("concentration", round(concentration));
        f.put("passThrough", round(passThrough));
        f.put("speed", round(speed));
        f.put("ring", round(ring));
        f.put("newAccount", round(newAccount));
        f.put("hub", round(hubRank));
        f.put("fanOut", round(fan));
        if (reasons.isEmpty()) {
            reasons.add("ordinary account: victim money is a small share of normal activity");
        }
        return new MuleScore(id, (int) Math.round(s * 100), reasons, f);
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
