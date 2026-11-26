package com.muletracer.io;

import com.muletracer.graph.TemporalGraph;
import com.muletracer.investigate.FreezeOrder;
import com.muletracer.investigate.Investigation;
import com.muletracer.investigate.Investigator;
import com.muletracer.model.Account;
import com.muletracer.model.Transaction;
import com.muletracer.sim.ScamScenarioGenerator.Scenario;
import com.muletracer.trace.TraceConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs the same complaint at several report delays and writes one JSON file the static web demo reads.
 * The demo page does no tracing itself: every number on it comes from this Java engine.
 */
public final class DemoExporter {

    /** Minutes after the victim's LAST transfer when the complaint reaches the bank. */
    public static final int[] DELAYS_MIN = {0, 15, 30, 60, 120, 240, 480};

    private DemoExporter() {
    }

    public static Map<String, Object> build(Scenario sc, long seed) {
        TemporalGraph g = sc.graph();
        Investigator inv = new Investigator(g, TraceConfig.defaults());
        long lastFraud = 0;
        List<String> fraudIds = new ArrayList<>();
        for (Transaction t : sc.fraudTxns()) {
            lastFraud = Math.max(lastFraud, t.timestamp());
            fraudIds.add(t.id());
        }

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("seed", seed);
        meta.put("generatedBy", "com.muletracer.io.DemoExporter");
        meta.put("graphAccounts", g.accountCount());
        meta.put("graphTransactions", g.transactionCount());
        meta.put("victim", sc.victimId());
        meta.put("scamStart", sc.scamStart());
        meta.put("lastFraudAt", lastFraud);
        meta.put("fraudTxnIds", fraudIds);
        root.put("meta", meta);

        List<Object> runs = new ArrayList<>();
        Set<String> involved = new HashSet<>();
        involved.add(sc.victimId());
        involved.addAll(sc.dormantMules());
        for (int d : DELAYS_MIN) {
            long reportTime = lastFraud + d * 60L;
            Investigation result = inv.investigate(sc.fraudTxns(), reportTime);
            Map<String, Object> m = InvestigationJson.toMap(result);
            m.put("delayMinutes", d);
            m.put("accuracy", accuracy(result, sc));
            runs.add(m);
            involved.addAll(result.trace().accounts().keySet());
            result.trace().edges().forEach(e -> involved.add(e.txn().to()));
        }
        root.put("runs", runs);

        Map<String, Object> accounts = new TreeMap<>();
        for (String id : involved) {
            Account a = g.account(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("bank", a.bank());
            m.put("city", a.city());
            m.put("ageDays", a.ageDays());
            m.put("device", a.deviceId());
            m.put("ip", a.ipHash());
            m.put("truth", truth(id, sc));
            accounts.put(id, m);
        }
        root.put("accounts", accounts);
        return root;
    }

    /** How the engine's actions compare with the generator's ground truth (only possible in simulation). */
    private static Map<String, Object> accuracy(Investigation inv, Scenario sc) {
        int tp = 0;
        int fp = 0;
        int innocentFrozen = 0;
        int innocentLien = 0;
        Set<String> flagged = new HashSet<>();
        for (FreezeOrder o : inv.freezeOrders()) {
            boolean hard = o.action().equals("FREEZE") || o.action().equals("BLOCK");
            boolean mule = sc.activeMules().contains(o.accountId());
            if (hard) {
                flagged.add(o.accountId());
                if (mule) {
                    tp++;
                } else {
                    fp++;
                    innocentFrozen++;
                }
            } else if (!mule) {
                innocentLien++;
            }
        }
        int tracedMules = 0;
        for (String id : inv.trace().accounts().keySet()) {
            if (sc.activeMules().contains(id)) {
                tracedMules++;
            }
        }
        int dormantCaught = 0;
        for (String w : inv.watchlist()) {
            if (sc.dormantMules().contains(w)) {
                dormantCaught++;
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("muleAccountsTotal", sc.activeMules().size());
        m.put("muleAccountsTraced", tracedMules);
        m.put("hardActions", flagged.size());
        m.put("precision", flagged.isEmpty() ? 1.0 : Math.round(1000.0 * tp / (tp + fp)) / 1000.0);
        m.put("innocentFrozen", innocentFrozen);
        m.put("innocentLienOnly", innocentLien);
        m.put("dormantMulesTotal", sc.dormantMules().size());
        m.put("dormantMulesWatchlisted", dormantCaught);
        return m;
    }

    private static String truth(String id, Scenario sc) {
        if (id.equals(sc.victimId())) {
            return "victim";
        }
        if (id.startsWith("CASH:")) {
            return "cash-out";
        }
        if (sc.activeMules().contains(id)) {
            return "mule";
        }
        if (sc.dormantMules().contains(id)) {
            return "dormant-mule";
        }
        if (sc.innocentRecipients().contains(id)) {
            return "innocent-shop";
        }
        return "customer";
    }

    public static void write(Map<String, Object> data, Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, Json.write(data), StandardCharsets.UTF_8);
    }
}
