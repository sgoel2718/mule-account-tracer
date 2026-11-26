package com.muletracer;

import com.muletracer.api.TracerServer;
import com.muletracer.bench.Benchmark;
import com.muletracer.investigate.FreezeOrder;
import com.muletracer.investigate.Investigation;
import com.muletracer.investigate.Investigator;
import com.muletracer.io.DemoExporter;
import com.muletracer.model.Transaction;
import com.muletracer.sim.ScamScenarioGenerator;
import com.muletracer.sim.ScamScenarioGenerator.Scenario;
import com.muletracer.trace.TraceConfig;
import com.muletracer.trace.TraceResult;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Command line entry point.
 *
 * <pre>
 * java -jar mule-account-tracer.jar demo [delayMinutes]   # trace the sample scam and print the freeze list
 * java -jar mule-account-tracer.jar export [file]         # write docs/data/scenario.json for the web demo
 * java -jar mule-account-tracer.jar serve [port]          # REST API on the sample scenario
 * java -jar mule-account-tracer.jar bench [accounts] [txns]
 * </pre>
 */
public final class App {

    public static final long SEED = 1930; // 1930 = India's national cyber-fraud helpline
    public static final int LEGIT_ACCOUNTS = 5_000;
    public static final int LEGIT_TXNS = 60_000;

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH).withZone(ZoneId.of("Asia/Kolkata"));

    private App() {
    }

    public static void main(String[] args) throws Exception {
        String cmd = args.length > 0 ? args[0] : "demo";
        switch (cmd) {
            case "demo" -> demo(args.length > 1 ? Integer.parseInt(args[1]) : 30);
            case "export" -> {
                Path out = Path.of(args.length > 1 ? args[1] : "docs/data/scenario.json");
                Scenario sc = ScamScenarioGenerator.generate(SEED, LEGIT_ACCOUNTS, LEGIT_TXNS);
                DemoExporter.write(DemoExporter.build(sc, SEED), out);
                System.out.println("wrote " + out.toAbsolutePath());
            }
            case "serve" -> {
                int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
                Scenario sc = ScamScenarioGenerator.generate(SEED, LEGIT_ACCOUNTS, LEGIT_TXNS);
                TracerServer server = new TracerServer(sc.graph(), port);
                server.start();
                StringBuilder utrs = new StringBuilder();
                for (Transaction t : sc.fraudTxns()) {
                    utrs.append(utrs.length() == 0 ? "" : ",").append(t.id());
                }
                long last = sc.fraudTxns().get(sc.fraudTxns().size() - 1).timestamp();
                System.out.printf("Mule Account Tracer API on http://localhost:%d%n", port);
                System.out.printf("try: curl \"http://localhost:%d/api/investigate?utr=%s&reportTime=%d\"%n",
                        port, utrs, last + 1800);
            }
            case "bench" -> Benchmark.run(
                    args.length > 1 ? Integer.parseInt(args[1]) : 200_000,
                    args.length > 2 ? Integer.parseInt(args[2]) : 2_000_000);
            default -> {
                System.err.println("usage: demo [delayMin] | export [file] | serve [port] | bench [accounts] [txns]");
                System.exit(2);
            }
        }
    }

    private static void demo(int delayMinutes) {
        Scenario sc = ScamScenarioGenerator.generate(SEED, LEGIT_ACCOUNTS, LEGIT_TXNS);
        Transaction last = sc.fraudTxns().get(sc.fraudTxns().size() - 1);
        long reportTime = last.timestamp() + delayMinutes * 60L;
        Investigation inv = new Investigator(sc.graph(), TraceConfig.defaults()).investigate(sc.fraudTxns(), reportTime);
        TraceResult tr = inv.trace();

        System.out.println("=== MULE ACCOUNT TRACER ===");
        System.out.printf("Graph: %,d accounts, %,d transactions%n", sc.graph().accountCount(), sc.graph().transactionCount());
        System.out.printf("Victim %s reported %d transfers; complaint received %s (%d min after last transfer)%n",
                sc.victimId(), sc.fraudTxns().size(), TIME.format(Instant.ofEpochSecond(reportTime)), delayMinutes);
        System.out.println();
        System.out.printf("Stolen          : %s%n", rupees(tr.fraudTotal()));
        System.out.printf("Still freezable : %s  (%.1f%%)%n", rupees(tr.freezable()), tr.recoveryRate() * 100);
        System.out.printf("Already cashed  : %s%n", rupees(tr.cashedOut()));
        System.out.printf("Trail           : %d accounts, %d tainted transfers, %d layers deep%n",
                tr.accounts().size(), tr.edges().size(), tr.maxHopReached());
        System.out.printf("Mule rings      : %d   Chokepoints (min vertex cut): %s%n",
                inv.rings().size(), inv.chokepoints().chokepoints());
        System.out.printf("Watchlist       : %d dormant ring accounts%n", inv.watchlist().size());
        System.out.printf("Investigation   : %.2f ms%n", inv.elapsedNanos() / 1e6);
        System.out.println();
        System.out.printf("%-4s %-17s %-9s %-4s %14s %5s  %-6s %s%n", "#", "ACCOUNT", "BANK", "HOP", "HELD", "SCORE", "ACTION", "WHY");
        int i = 1;
        for (FreezeOrder o : inv.freezeOrders()) {
            System.out.printf("%-4d %-17s %-9s %-4d %14s %5d  %-6s %s%n", i++, o.accountId(), o.bank(), o.hop(),
                    rupees(o.heldPaise()), o.score(), o.action(), String.join("; ", o.reasons()));
        }
    }

    /** Formats paise as Indian-grouped rupees, e.g. 185000000 -> "Rs 18,50,000". */
    public static String rupees(long paise) {
        long r = paise / 100;
        String s = Long.toString(r);
        if (s.length() <= 3) {
            return "Rs " + s;
        }
        String last3 = s.substring(s.length() - 3);
        String rest = s.substring(0, s.length() - 3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rest.length(); i++) {
            if (i > 0 && (rest.length() - i) % 2 == 0) {
                sb.append(',');
            }
            sb.append(rest.charAt(i));
        }
        return "Rs " + sb + "," + last3;
    }
}
