package com.muletracer.io;

import com.muletracer.cluster.MuleRing;
import com.muletracer.investigate.FreezeOrder;
import com.muletracer.investigate.Investigation;
import com.muletracer.score.MuleScore;
import com.muletracer.trace.AccountTaint;
import com.muletracer.trace.TaintedEdge;
import com.muletracer.trace.TraceResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Converts an {@link Investigation} into plain maps/lists for {@link Json}. Amounts stay in paise. */
public final class InvestigationJson {

    private InvestigationJson() {
    }

    public static Map<String, Object> toMap(Investigation inv) {
        TraceResult tr = inv.trace();
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("reportTime", tr.reportTime());
        summary.put("fraudTotal", tr.fraudTotal());
        summary.put("freezable", tr.freezable());
        summary.put("cashedOut", tr.cashedOut());
        summary.put("beyondHopLimit", tr.beyondHopLimit());
        summary.put("recoveryRate", Math.round(tr.recoveryRate() * 10000) / 10000.0);
        summary.put("tracedAccounts", tr.accounts().size());
        summary.put("taintedTransactions", tr.edges().size());
        summary.put("maxHop", tr.maxHopReached());
        summary.put("eventsProcessed", tr.eventsProcessed());
        summary.put("elapsedMicros", inv.elapsedNanos() / 1000);
        root.put("summary", summary);

        Map<String, Object> accounts = new TreeMap<>();
        for (AccountTaint at : tr.accounts().values()) {
            MuleScore ms = inv.scores().get(at.accountId());
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("hop", at.hop());
            a.put("received", at.received());
            a.put("held", at.held());
            a.put("cashedOut", at.cashedOut());
            a.put("firstTaintedAt", at.firstTaintedAt());
            a.put("score", ms.score());
            a.put("verdict", ms.verdict());
            a.put("reasons", ms.reasons());
            a.put("features", ms.features());
            accounts.put(at.accountId(), a);
        }
        root.put("accounts", accounts);

        List<Object> edges = new ArrayList<>();
        for (TaintedEdge e : tr.edges()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.txn().id());
            m.put("from", e.txn().from());
            m.put("to", e.txn().to());
            m.put("amount", e.txn().amount());
            m.put("tainted", e.taintedAmount());
            m.put("ts", e.txn().timestamp());
            m.put("channel", e.txn().channel().name());
            m.put("hop", e.hop());
            edges.add(m);
        }
        root.put("edges", edges);

        List<Object> orders = new ArrayList<>();
        int rank = 1;
        for (FreezeOrder o : inv.freezeOrders()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", rank++);
            m.put("account", o.accountId());
            m.put("bank", o.bank());
            m.put("city", o.city());
            m.put("hop", o.hop());
            m.put("held", o.heldPaise());
            m.put("score", o.score());
            m.put("verdict", o.verdict());
            m.put("action", o.action());
            m.put("chokepoint", o.chokepoint());
            m.put("ring", o.ringId());
            m.put("reasons", o.reasons());
            orders.add(m);
        }
        root.put("freezeOrders", orders);

        Map<String, Object> cut = new LinkedHashMap<>();
        cut.put("accounts", inv.chokepoints().chokepoints());
        cut.put("disjointCashOutRoutes", inv.chokepoints().disjointCashOutRoutes());
        cut.put("cashOutPoints", inv.chokepoints().cashOutPoints());
        root.put("chokepoints", cut);

        List<Object> rings = new ArrayList<>();
        for (MuleRing r : inv.rings()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.id());
            m.put("members", r.members());
            m.put("tainted", new ArrayList<>(r.taintedMembers()));
            m.put("dormant", r.dormantMembers());
            m.put("sharedSignals", new ArrayList<>(r.sharedSignals()));
            rings.add(m);
        }
        root.put("rings", rings);
        root.put("watchlist", inv.watchlist());
        return root;
    }
}
