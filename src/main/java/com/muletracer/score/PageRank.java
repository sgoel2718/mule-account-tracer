package com.muletracer.score;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Weighted PageRank by power iteration.
 *
 * <p>Used on the money-flow subgraph: an account that many tainted accounts funnel money into
 * (an aggregator / "boss" mule) collects rank, just like a page many pages link to.</p>
 *
 * <p>Complexity: O(iterations * (V + E)).</p>
 */
public final class PageRank {

    private final Map<String, Integer> idx = new HashMap<>();
    private final List<String> nodes = new ArrayList<>();
    private final List<int[]> edgeEnds = new ArrayList<>();
    private final List<Double> weights = new ArrayList<>();

    public void addEdge(String from, String to, double weight) {
        if (weight <= 0) {
            return;
        }
        edgeEnds.add(new int[]{node(from), node(to)});
        weights.add(weight);
    }

    public void addNode(String id) {
        node(id);
    }

    private int node(String id) {
        return idx.computeIfAbsent(id, k -> {
            nodes.add(k);
            return nodes.size() - 1;
        });
    }

    /** @return rank per node, scaled so the highest rank is 1.0 */
    public Map<String, Double> compute(double damping, int iterations) {
        int n = nodes.size();
        Map<String, Double> out = new HashMap<>();
        if (n == 0) {
            return out;
        }
        double[] outW = new double[n];
        for (int e = 0; e < edgeEnds.size(); e++) {
            outW[edgeEnds.get(e)[0]] += weights.get(e);
        }
        double[] rank = new double[n];
        java.util.Arrays.fill(rank, 1.0 / n);
        for (int it = 0; it < iterations; it++) {
            double[] next = new double[n];
            double dangling = 0;
            for (int v = 0; v < n; v++) {
                if (outW[v] == 0) {
                    dangling += rank[v];
                }
            }
            double base = (1 - damping) / n + damping * dangling / n;
            java.util.Arrays.fill(next, base);
            for (int e = 0; e < edgeEnds.size(); e++) {
                int u = edgeEnds.get(e)[0];
                int v = edgeEnds.get(e)[1];
                next[v] += damping * rank[u] * weights.get(e) / outW[u];
            }
            rank = next;
        }
        double max = 0;
        for (double r : rank) {
            max = Math.max(max, r);
        }
        for (int v = 0; v < n; v++) {
            out.put(nodes.get(v), rank[v] / max);
        }
        return out;
    }
}
