package com.muletracer.cut;

import com.muletracer.model.Transaction;
import com.muletracer.trace.TaintedEdge;
import com.muletracer.trace.TraceResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the <b>smallest set of mule accounts</b> whose freeze disconnects the victim from every
 * cash-out point seen in the trace: a minimum vertex cut.
 *
 * <p>By Menger's theorem the minimum vertex cut equals the max number of vertex-disjoint paths, so we
 * build a flow network and run {@link Dinic}:</p>
 * <ul>
 *   <li>every mule account v becomes {@code v_in -> v_out} with capacity 1 (freezing it costs one action)</li>
 *   <li>victims, cash-out sinks and money-flow edges get infinite capacity (they cannot be "cut")</li>
 *   <li>super-source -> victims, cash sinks -> super-sink</li>
 * </ul>
 * <p>After max-flow, a mule is in the cut when {@code v_in} is reachable from the source in the residual
 * graph but {@code v_out} is not. Every observed cash-out route passed through these accounts, so blocking
 * them shuts the gang's pipeline with the fewest requests: the next victim's money has nowhere to go.</p>
 */
public final class ChokepointFinder {

    public record Result(List<String> chokepoints, int disjointCashOutRoutes, int cashOutPoints) {
    }

    public Result find(TraceResult trace) {
        Set<String> victims = trace.victims();
        Set<String> sinks = new HashSet<>();
        Set<String> accounts = new HashSet<>(victims);
        List<Transaction> flow = new ArrayList<>();
        for (TaintedEdge e : trace.edges()) {
            Transaction t = e.txn();
            flow.add(t);
            accounts.add(t.from());
            if (t.channel().isCashOut() || t.to().startsWith("CASH:")) {
                sinks.add(t.to());
            } else {
                accounts.add(t.to());
            }
        }
        if (sinks.isEmpty()) {
            return new Result(List.of(), 0, 0);
        }
        accounts.removeAll(sinks);

        Map<String, Integer> id = new HashMap<>();
        List<String> names = new ArrayList<>();
        for (String a : accounts) {
            id.put(a, names.size());
            names.add(a);
        }
        int m = names.size();
        Map<String, Integer> sinkId = new HashMap<>();
        for (String s : sinks) {
            sinkId.put(s, 2 * m + sinkId.size());
        }
        int source = 2 * m + sinks.size();
        int sink = source + 1;
        Dinic d = new Dinic(sink + 1);

        for (int i = 0; i < m; i++) {
            boolean uncuttable = victims.contains(names.get(i));
            d.addEdge(2 * i, 2 * i + 1, uncuttable ? Dinic.INF : 1);
        }
        for (String v : victims) {
            d.addEdge(source, 2 * id.get(v), Dinic.INF);
        }
        for (int s : sinkId.values()) {
            d.addEdge(s, sink, Dinic.INF);
        }
        Set<String> seenEdge = new HashSet<>();
        for (Transaction t : flow) {
            Integer u = id.get(t.from());
            if (u == null) {
                continue;
            }
            int target = sinkId.containsKey(t.to()) ? sinkId.get(t.to()) : 2 * id.get(t.to());
            if (seenEdge.add(t.from() + "->" + t.to())) {
                d.addEdge(2 * u + 1, target, Dinic.INF);
            }
        }

        long flowValue = d.maxFlow(source, sink);
        boolean[] reach = d.sourceSide(source);
        List<String> cut = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            if (reach[2 * i] && !reach[2 * i + 1]) {
                cut.add(names.get(i));
            }
        }
        Collections.sort(cut);
        return new Result(cut, (int) Math.min(flowValue, Integer.MAX_VALUE), sinks.size());
    }
}
