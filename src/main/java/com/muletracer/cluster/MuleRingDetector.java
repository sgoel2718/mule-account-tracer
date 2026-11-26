package com.muletracer.cluster;

import com.muletracer.graph.TemporalGraph;
import com.muletracer.model.Account;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Finds mule rings: one fraud operator usually runs dozens of "rented" accounts from a handful of phones.
 *
 * <p>Every account is a node in a union-find. Accounts that share a device fingerprint or IP hash are
 * unioned as they are registered, so the structure is maintained <b>incrementally</b>: a new login event
 * costs O(alpha(n)), and each connected component is a ring. Member lists are merged on union so a
 * query only touches the rings that the trace actually hit, never the whole customer base.</p>
 *
 * <ul>
 *   <li>{@link #register}: O(alpha(n)) amortised plus member-list merge</li>
 *   <li>{@link #detect}: O(T * alpha(n) + size of touched rings), T = tainted accounts</li>
 * </ul>
 */
public final class MuleRingDetector {

    private final UnionFind uf = new UnionFind();
    private final Map<String, String> firstBySignal = new HashMap<>();
    private final Map<String, List<String>> membersByRoot = new HashMap<>();
    private final Map<String, Account> accounts = new HashMap<>();

    /** Indexes every account already in the graph. */
    public MuleRingDetector(TemporalGraph graph) {
        for (Account a : graph.accounts()) {
            register(a);
        }
    }

    /** Adds (or re-adds) an account and links it to any account sharing its device or network. */
    public synchronized void register(Account a) {
        if (a.isCashSink() || accounts.containsKey(a.id())) {
            return;
        }
        accounts.put(a.id(), a);
        uf.add(a.id());
        List<String> own = new ArrayList<>();
        own.add(a.id());
        membersByRoot.put(a.id(), own);
        link("dev:" + a.deviceId(), a.deviceId(), a.id());
        link("ip:" + a.ipHash(), a.ipHash(), a.id());
    }

    private void link(String key, String raw, String id) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String first = firstBySignal.putIfAbsent(key, id);
        if (first == null) {
            return;
        }
        String ra = uf.find(first);
        String rb = uf.find(id);
        if (uf.union(ra, rb)) {
            String root = uf.find(id);
            String gone = root.equals(ra) ? rb : ra;
            membersByRoot.get(root).addAll(membersByRoot.remove(gone));
        }
    }

    /**
     * @param tainted accounts that received victim money in the trace
     * @return rings with at least two members and at least one tainted member, largest first
     */
    public synchronized List<MuleRing> detect(Set<String> tainted) {
        Set<String> roots = new LinkedHashSet<>();
        for (String t : tainted) {
            if (accounts.containsKey(t)) {
                roots.add(uf.find(t));
            }
        }
        List<List<String>> components = new ArrayList<>();
        for (String r : roots) {
            List<String> group = new ArrayList<>(membersByRoot.get(r));
            if (group.size() >= 2) {
                Collections.sort(group);
                components.add(group);
            }
        }
        components.sort(Comparator.<List<String>>comparingInt(List::size).reversed()
                .thenComparing(l -> l.get(0)));

        List<MuleRing> rings = new ArrayList<>();
        int n = 1;
        for (List<String> group : components) {
            Set<String> taintedMembers = new TreeSet<>();
            List<String> dormant = new ArrayList<>();
            Map<String, Integer> signalCount = new HashMap<>();
            for (String m : group) {
                if (tainted.contains(m)) {
                    taintedMembers.add(m);
                } else {
                    dormant.add(m);
                }
                Account a = accounts.get(m);
                if (a.deviceId() != null) {
                    signalCount.merge("device " + a.deviceId(), 1, Integer::sum);
                }
                if (a.ipHash() != null) {
                    signalCount.merge("ip " + a.ipHash(), 1, Integer::sum);
                }
            }
            Set<String> shared = new TreeSet<>();
            signalCount.forEach((sig, c) -> {
                if (c > 1) {
                    shared.add(sig);
                }
            });
            rings.add(new MuleRing("R" + n++, group, taintedMembers, dormant, shared));
        }
        return rings;
    }
}
