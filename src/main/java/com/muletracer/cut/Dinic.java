package com.muletracer.cut;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Dinic's max-flow: BFS builds a level graph, DFS pushes blocking flows along it.
 * O(V^2 * E) in general, O(E * sqrt(V)) on unit-capacity graphs like the vertex-cut network here.
 */
public final class Dinic {

    public static final long INF = Long.MAX_VALUE / 4;

    private final int n;
    private final List<int[]> adj; // edge indices per node
    private int[] to = new int[16];
    private long[] cap = new long[16];
    private int edges;
    private int[] level;
    private int[] it;

    public Dinic(int n) {
        this.n = n;
        this.adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new int[0]);
        }
    }

    private final List<List<Integer>> adjBuild = new ArrayList<>();

    /** Adds a directed edge u -> v with capacity c (and its residual twin). */
    public void addEdge(int u, int v, long c) {
        ensureBuild();
        if (edges + 2 > to.length) {
            to = Arrays.copyOf(to, to.length * 2);
            cap = Arrays.copyOf(cap, cap.length * 2);
        }
        to[edges] = v;
        cap[edges] = c;
        adjBuild.get(u).add(edges++);
        to[edges] = u;
        cap[edges] = 0;
        adjBuild.get(v).add(edges++);
    }

    private void ensureBuild() {
        while (adjBuild.size() < n) {
            adjBuild.add(new ArrayList<>());
        }
    }

    public long maxFlow(int s, int t) {
        ensureBuild();
        for (int i = 0; i < n; i++) {
            List<Integer> l = adjBuild.get(i);
            int[] a = new int[l.size()];
            for (int k = 0; k < a.length; k++) {
                a[k] = l.get(k);
            }
            adj.set(i, a);
        }
        long flow = 0;
        level = new int[n];
        it = new int[n];
        while (bfs(s, t)) {
            Arrays.fill(it, 0);
            long f;
            while ((f = dfs(s, t, INF)) > 0) {
                flow += f;
            }
        }
        return flow;
    }

    private boolean bfs(int s, int t) {
        Arrays.fill(level, -1);
        ArrayDeque<Integer> q = new ArrayDeque<>();
        level[s] = 0;
        q.add(s);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int e : adj.get(u)) {
                if (cap[e] > 0 && level[to[e]] < 0) {
                    level[to[e]] = level[u] + 1;
                    q.add(to[e]);
                }
            }
        }
        return level[t] >= 0;
    }

    private long dfs(int u, int t, long pushed) {
        if (u == t) {
            return pushed;
        }
        int[] edgesOfU = adj.get(u);
        for (; it[u] < edgesOfU.length; it[u]++) {
            int e = edgesOfU[it[u]];
            int v = to[e];
            if (cap[e] > 0 && level[v] == level[u] + 1) {
                long d = dfs(v, t, Math.min(pushed, cap[e]));
                if (d > 0) {
                    cap[e] -= d;
                    cap[e ^ 1] += d;
                    return d;
                }
            }
        }
        return 0;
    }

    /** After {@link #maxFlow}, which nodes are reachable from s in the residual graph (the source side of the min cut). */
    public boolean[] sourceSide(int s) {
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        seen[s] = true;
        q.add(s);
        while (!q.isEmpty()) {
            int u = q.poll();
            for (int e : adj.get(u)) {
                if (cap[e] > 0 && !seen[to[e]]) {
                    seen[to[e]] = true;
                    q.add(to[e]);
                }
            }
        }
        return seen;
    }
}
