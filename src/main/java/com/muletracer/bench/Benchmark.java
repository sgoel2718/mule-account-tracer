package com.muletracer.bench;

import com.muletracer.investigate.Investigator;
import com.muletracer.model.Transaction;
import com.muletracer.sim.ScamScenarioGenerator;
import com.muletracer.sim.ScamScenarioGenerator.Scenario;
import com.muletracer.trace.TaintTracer;
import com.muletracer.trace.TraceConfig;

import java.util.Arrays;

/**
 * Measures latency of a full investigation on a large synthetic graph.
 * Plain System.nanoTime loop with warm-up; good enough for orders of magnitude (use JMH for publication).
 */
public final class Benchmark {

    private Benchmark() {
    }

    public static void run(int accounts, int txns) {
        long g0 = System.nanoTime();
        Scenario sc = ScamScenarioGenerator.generate(1930, accounts, txns);
        System.out.printf("built graph: %,d accounts, %,d transactions in %.1f s%n",
                sc.graph().accountCount(), sc.graph().transactionCount(), (System.nanoTime() - g0) / 1e9);

        long i0 = System.nanoTime();
        Investigator inv = new Investigator(sc.graph(), TraceConfig.defaults());
        System.out.printf("ring index built in %.1f ms%n", (System.nanoTime() - i0) / 1e6);

        Transaction last = sc.fraudTxns().get(sc.fraudTxns().size() - 1);
        long reportTime = last.timestamp() + 8 * 3600;
        TaintTracer tracer = new TaintTracer(sc.graph(), TraceConfig.defaults());

        int warm = 200;
        int runs = 1000;
        for (int i = 0; i < warm; i++) {
            inv.investigate(sc.fraudTxns(), reportTime);
        }
        long[] traceNs = new long[runs];
        long[] fullNs = new long[runs];
        for (int i = 0; i < runs; i++) {
            long a = System.nanoTime();
            tracer.trace(sc.fraudTxns(), reportTime);
            long b = System.nanoTime();
            inv.investigate(sc.fraudTxns(), reportTime);
            long c = System.nanoTime();
            traceNs[i] = b - a;
            fullNs[i] = c - b;
        }
        print("trace only", traceNs);
        print("full investigation (trace+rings+scores+min-cut+freeze list)", fullNs);

        // ingestion throughput: re-insert all transactions into a fresh graph
        var fresh = new com.muletracer.graph.TemporalGraph();
        var all = new java.util.ArrayList<>(sc.graph().transactions());
        all.sort(java.util.Comparator.comparingLong(Transaction::timestamp));
        long s0 = System.nanoTime();
        for (Transaction t : all) {
            fresh.addTransaction(t);
        }
        double sec = (System.nanoTime() - s0) / 1e9;
        System.out.printf("ingestion: %,d txns in %.2f s = %,.0f txns/sec%n", all.size(), sec, all.size() / sec);
    }

    private static void print(String label, long[] ns) {
        long[] s = ns.clone();
        Arrays.sort(s);
        System.out.printf("%-62s p50 %7.1f us   p99 %7.1f us%n", label,
                s[s.length / 2] / 1e3, s[(int) (s.length * 0.99)] / 1e3);
    }
}
