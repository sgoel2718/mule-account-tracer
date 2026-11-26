package com.muletracer.sim;

import com.muletracer.graph.TemporalGraph;
import com.muletracer.model.Account;
import com.muletracer.model.Channel;
import com.muletracer.model.Transaction;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Builds a synthetic but realistic "digital arrest" scam on top of ordinary banking traffic.
 *
 * <p>The story: a retired professor in Chennai gets a video call from a fake "CBI officer", is told
 * his Aadhaar is linked to money laundering and must move his savings to an "RBI verification account".
 * Over ~50 minutes he sends Rs 18.5 lakh in four transfers. The gang then:</p>
 * <ol>
 *   <li>L1: four freshly opened accounts receive his transfers,</li>
 *   <li>L2: within minutes each splits the money across 3 more mules (classic layering),</li>
 *   <li>L3: three aggregator accounts collect most of it and sell it for USDT on P2P crypto desks,</li>
 *   <li>L4: two more accounts pull cash from ATMs in Jamtara, Nuh and Bharatpur over the next hours.</li>
 * </ol>
 * <p>Mules also pay a few real shops (innocent recipients the tracer must not blindly freeze), and the
 * gang keeps dormant accounts on the same phones (the ring detector should catch these).</p>
 *
 * <p>Everything is seeded, so a given seed always produces the same scenario.</p>
 */
public final class ScamScenarioGenerator {

    public record Scenario(TemporalGraph graph, List<Transaction> fraudTxns, String victimId,
                           Set<String> activeMules, Set<String> dormantMules, Set<String> innocentRecipients,
                           long scamStart) {
    }

    private static final String[] LEGIT_BANKS = {"SBI", "HDFC", "ICICI", "AXIS", "KOTAK", "PNB", "BOB", "CANARA", "IOB", "INDIAN"};
    private static final String[] LEGIT_CITIES = {"Chennai", "Bengaluru", "Hyderabad", "Mumbai", "Pune", "Delhi", "Kolkata", "Coimbatore", "Madurai", "Jaipur"};
    private static final String[] MULE_BANKS = {"AUSFB", "EQUITAS", "UJJIVAN", "JANA", "COOP-NUH", "PAYTMPB", "AIRTELPB", "FINO"};
    private static final String[] MULE_CITIES = {"Jamtara", "Nuh", "Bharatpur", "Deoghar", "Kolkata", "Delhi", "Mathura", "Alwar"};

    private static final long MIN = 60;
    private static final long RUPEE = 100;

    private final Random rnd;
    private final TemporalGraph g = new TemporalGraph();
    private final List<String> legit = new ArrayList<>();
    private final List<String> merchants = new ArrayList<>();
    private final Set<String> usedIds = new HashSet<>();
    private int seq;

    private ScamScenarioGenerator(long seed) {
        this.rnd = new Random(seed);
    }

    /** Scam start: 20 Nov 2026, 10:00 IST. */
    public static long defaultStart() {
        return ZonedDateTime.of(2026, 11, 20, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toEpochSecond();
    }

    public static Scenario generate(long seed, int legitAccounts, int legitTxns) {
        return new ScamScenarioGenerator(seed).build(legitAccounts, legitTxns, defaultStart());
    }

    private Scenario build(int legitAccounts, int legitTxns, long start) {
        // ---- ordinary customers ----
        String sharedIp = null;
        for (int i = 0; i < legitAccounts; i++) {
            String bank = pick(LEGIT_BANKS);
            String id = newId(bank);
            String ip = "IP-" + Integer.toHexString(rnd.nextInt(1 << 24) | (1 << 24)).substring(1);
            if (sharedIp != null) { // two members of the same household
                ip = sharedIp;
                sharedIp = null;
            } else if (rnd.nextDouble() < 0.04) {
                sharedIp = ip;
            }
            g.addAccount(new Account(id, bank, pick(LEGIT_CITIES), 200 + rnd.nextInt(4000),
                    "DEV-L" + i, ip));
            legit.add(id);
            if (i < Math.max(1, legitAccounts / 20)) {
                merchants.add(id);
            }
        }
        for (int i = 0; i < legitTxns; i++) {
            String from = legit.get(rnd.nextInt(legit.size()));
            String to = rnd.nextDouble() < 0.4 ? merchants.get(rnd.nextInt(merchants.size()))
                    : legit.get(rnd.nextInt(legit.size()));
            if (from.equals(to)) {
                continue;
            }
            long ts = start - 24 * 3600 + (long) (rnd.nextDouble() * 34 * 3600);
            long amount = (long) Math.pow(10, 2 + rnd.nextDouble() * 2.7) * RUPEE;
            g.addTransaction(txn(from, to, amount, ts, rnd.nextDouble() < 0.85 ? Channel.UPI : Channel.IMPS));
        }

        // ---- victim ----
        String victim = newId("SBI");
        g.addAccount(new Account(victim, "SBI", "Chennai", 9100, "DEV-VICTIM", "IP-VICTIM"));

        // ---- mule rings ----
        Set<String> active = new HashSet<>();
        Set<String> dormant = new HashSet<>();
        String[] ring1Dev = {"DEV-7f3a", "DEV-91c2"};
        String[] ring2Dev = {"DEV-c44d", "DEV-e019"};
        List<String> l1 = new ArrayList<>();
        List<String> l2 = new ArrayList<>();
        List<String> l3 = new ArrayList<>();
        List<String> l4 = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            l1.add(mule(ring1Dev[i % 2], "IP-ab12", 8 + rnd.nextInt(35), active));
        }
        for (int i = 0; i < 12; i++) {
            boolean r1 = i < 6;
            l2.add(mule(r1 ? ring1Dev[i % 2] : ring2Dev[i % 2], r1 ? "IP-ab12" : "IP-77fe", 5 + rnd.nextInt(60), active));
        }
        for (int i = 0; i < 3; i++) {
            l3.add(mule(ring2Dev[i % 2], "IP-77fe", 20 + rnd.nextInt(70), active));
        }
        for (int i = 0; i < 2; i++) {
            l4.add(mule(ring2Dev[i % 2], "IP-77fe", 3 + rnd.nextInt(20), active));
        }
        for (int i = 0; i < 6; i++) {
            boolean r1 = i < 3;
            dormant.add(mule(r1 ? ring1Dev[i % 2] : ring2Dev[i % 2], r1 ? "IP-ab12" : "IP-77fe", 2 + rnd.nextInt(25), null));
        }
        // a little "account testing" history days earlier, as real mules have
        for (String m : active) {
            String from = legit.get(rnd.nextInt(legit.size()));
            g.addTransaction(txn(from, m, (500 + rnd.nextInt(4500)) * RUPEE,
                    start - (2 + rnd.nextInt(10)) * 24 * 3600L - rnd.nextInt(80000), Channel.UPI));
        }

        // ---- the scam ----
        long[] fraudRupees = {950_000, 400_000, 300_000, 200_000};
        long[] fraudOffsetMin = {0, 14, 31, 52};
        Channel[] fraudChannel = {Channel.RTGS, Channel.IMPS, Channel.IMPS, Channel.IMPS};
        List<Transaction> fraud = new ArrayList<>();
        Set<String> innocent = new HashSet<>();
        int l2Next = 0;
        for (int i = 0; i < 4; i++) {
            long ts = start + fraudOffsetMin[i] * MIN;
            Transaction f = txn(victim, l1.get(i), fraudRupees[i] * RUPEE, ts, fraudChannel[i]);
            g.addTransaction(f);
            fraud.add(f);

            // L1 -> 3 x L2, keep ~2% as commission
            long toSplit = f.amount() * 98 / 100;
            long[] parts = split(toSplit, 3);
            long t = ts + (2 + rnd.nextInt(5)) * MIN;
            for (int k = 0; k < 3; k++) {
                String child = l2.get(l2Next++);
                g.addTransaction(txn(l1.get(i), child, parts[k], t, Channel.IMPS));
                layerTwo(child, parts[k], t, l3, l4, innocent);
                t += (1 + rnd.nextInt(3)) * MIN;
            }
        }
        return new Scenario(g, fraud, victim, active, dormant, innocent, start);
    }

    /** L2 mule: 65% to an aggregator, ~25% ATM cash, sometimes pays a real shop, rest stays. */
    private void layerTwo(String m, long received, long receivedAt, List<String> l3, List<String> l4, Set<String> innocent) {
        long toAgg = received * 65 / 100;
        String agg = l3.get(rnd.nextInt(l3.size()));
        long t = receivedAt + (4 + rnd.nextInt(12)) * MIN;
        g.addTransaction(txn(m, agg, toAgg, t, Channel.IMPS));
        layerThree(agg, toAgg, t, l4);

        long cash = received * 25 / 100;
        atm(m, cash, receivedAt + (20 + rnd.nextInt(70)) * MIN);

        if (rnd.nextDouble() < 0.4) {
            String shop = merchants.get(rnd.nextInt(merchants.size()));
            g.addTransaction(txn(m, shop, (800 + rnd.nextInt(1700)) * RUPEE,
                    receivedAt + (25 + rnd.nextInt(20)) * MIN, Channel.UPI));
            innocent.add(shop);
        }
    }

    /** L3 aggregator: half sold for USDT on P2P desks, a third pushed to L4 cash-out mules. */
    private void layerThree(String agg, long received, long receivedAt, List<String> l4) {
        long t = receivedAt + (10 + rnd.nextInt(30)) * MIN;
        long crypto = received * 50 / 100;
        g.addTransaction(txn(agg, "CASH:P2P-USDT-" + (1 + rnd.nextInt(4)), crypto, t, Channel.CRYPTO_P2P));
        long onward = received * 35 / 100;
        String target = l4.get(rnd.nextInt(l4.size()));
        long t2 = t + (3 + rnd.nextInt(10)) * MIN;
        g.addTransaction(txn(agg, target, onward, t2, Channel.IMPS));
        atm(target, onward * 90 / 100, t2 + (40 + rnd.nextInt(80)) * MIN);
    }

    /** Withdraws in ATM-sized chunks (<= Rs 49,500), a few minutes apart, from a mule hotspot. */
    private void atm(String from, long amount, long startTs) {
        String city = pick(MULE_CITIES).toUpperCase();
        String sink = "CASH:ATM-" + city + "-" + (100 + rnd.nextInt(900));
        long chunk = 49_500 * RUPEE;
        long t = startTs;
        while (amount > 0) {
            long a = Math.min(chunk, amount);
            g.addTransaction(txn(from, sink, a, t, Channel.ATM_WITHDRAWAL));
            amount -= a;
            t += (2 + rnd.nextInt(4)) * MIN;
        }
    }

    private String mule(String device, String ip, int ageDays, Set<String> registry) {
        String bank = pick(MULE_BANKS);
        String id = newId(bank);
        g.addAccount(new Account(id, bank, pick(MULE_CITIES), ageDays, device, ip));
        if (registry != null) {
            registry.add(id);
        }
        return id;
    }

    private long[] split(long total, int k) {
        double[] w = new double[k];
        double sum = 0;
        for (int i = 0; i < k; i++) {
            w[i] = 0.6 + rnd.nextDouble();
            sum += w[i];
        }
        long[] out = new long[k];
        long assigned = 0;
        for (int i = 0; i < k - 1; i++) {
            out[i] = (long) (total * w[i] / sum) / RUPEE * RUPEE;
            assigned += out[i];
        }
        out[k - 1] = total - assigned;
        return out;
    }

    private Transaction txn(String from, String to, long amount, long ts, Channel ch) {
        return new Transaction(String.format("UTR%09d", ++seq), from, to, amount, ts, ch);
    }

    private String newId(String bank) {
        String id;
        do {
            id = bank + "-" + (10_000_000 + rnd.nextInt(90_000_000));
        } while (!usedIds.add(id));
        return id;
    }

    private <T> T pick(T[] arr) {
        return arr[rnd.nextInt(arr.length)];
    }
}
