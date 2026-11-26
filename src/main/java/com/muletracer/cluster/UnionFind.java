package com.muletracer.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Disjoint-set union over string keys with path compression and union by rank.
 * Both operations run in O(alpha(n)) amortised, effectively constant.
 */
public final class UnionFind {

    private final Map<String, Integer> index = new HashMap<>();
    private final List<String> keys = new ArrayList<>();
    private int[] parent = new int[16];
    private int[] rank = new int[16];
    private int components;

    /** Registers a key as its own singleton set (no-op if present). Returns its internal index. */
    public int add(String key) {
        Integer existing = index.get(key);
        if (existing != null) {
            return existing;
        }
        int i = keys.size();
        if (i == parent.length) {
            parent = java.util.Arrays.copyOf(parent, i * 2);
            rank = java.util.Arrays.copyOf(rank, i * 2);
        }
        parent[i] = i;
        rank[i] = 0;
        keys.add(key);
        index.put(key, i);
        components++;
        return i;
    }

    public String find(String key) {
        Integer i = index.get(key);
        if (i == null) {
            throw new IllegalArgumentException("unknown key " + key);
        }
        return keys.get(root(i));
    }

    private int root(int i) {
        int r = i;
        while (parent[r] != r) {
            r = parent[r];
        }
        while (parent[i] != r) { // path compression
            int next = parent[i];
            parent[i] = r;
            i = next;
        }
        return r;
    }

    /** Merges the sets of a and b (adding them if new). Returns true if they were in different sets. */
    public boolean union(String a, String b) {
        int ra = root(add(a));
        int rb = root(add(b));
        if (ra == rb) {
            return false;
        }
        if (rank[ra] < rank[rb]) {
            int tmp = ra;
            ra = rb;
            rb = tmp;
        }
        parent[rb] = ra;
        if (rank[ra] == rank[rb]) {
            rank[ra]++;
        }
        components--;
        return true;
    }

    public boolean connected(String a, String b) {
        return index.containsKey(a) && index.containsKey(b) && root(index.get(a)) == root(index.get(b));
    }

    public int components() {
        return components;
    }

    public int size() {
        return keys.size();
    }

    /** Groups every key by its root. */
    public Map<String, List<String>> groups() {
        Map<String, List<String>> out = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            out.computeIfAbsent(keys.get(root(i)), k -> new ArrayList<>()).add(keys.get(i));
        }
        return out;
    }
}
