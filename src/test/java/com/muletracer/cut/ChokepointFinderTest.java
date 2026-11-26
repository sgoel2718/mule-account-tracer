package com.muletracer.cut;

import com.muletracer.TestData;
import com.muletracer.model.Transaction;
import com.muletracer.trace.TaintTracer;
import com.muletracer.trace.TraceConfig;
import com.muletracer.trace.TraceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChokepointFinderTest {

    private static ChokepointFinder.Result run(TestData d, List<Transaction> fraud) {
        TraceResult r = new TaintTracer(d.graph, TraceConfig.defaults()).trace(fraud, 10_000 * 60);
        return new ChokepointFinder().find(r);
    }

    @Test
    void findsTheSingleAggregatorEveryPathGoesThrough() {
        // victim -> M1, M2, M3 -> AGG -> ATM
        TestData d = new TestData();
        Transaction f1 = d.pay("V", "M1", 100, 0);
        Transaction f2 = d.pay("V", "M2", 100, 0);
        Transaction f3 = d.pay("V", "M3", 100, 0);
        d.pay("M1", "AGG", 100, 5);
        d.pay("M2", "AGG", 100, 5);
        d.pay("M3", "AGG", 100, 5);
        d.atm("AGG", 300, 10);

        ChokepointFinder.Result res = run(d, List.of(f1, f2, f3));
        assertEquals(List.of("AGG"), res.chokepoints());
        assertEquals(1, res.disjointCashOutRoutes());
    }

    @Test
    void cutSizeEqualsNumberOfVertexDisjointRoutes() {
        // two independent chains to two ATMs: need to freeze one account per chain
        TestData d = new TestData();
        Transaction f = d.pay("V", "A", 200, 0);
        d.pay("A", "B1", 100, 1);
        d.pay("A", "B2", 100, 1);
        d.atm("B1", 100, 5);
        d.pay("B2", "CASH:ATM-OTHER", 100, 5, com.muletracer.model.Channel.ATM_WITHDRAWAL);
        // A is on both routes, so the minimum cut is just {A}
        ChokepointFinder.Result res = run(d, List.of(f));
        assertEquals(List.of("A"), res.chokepoints());

        TestData e = new TestData();
        Transaction g1 = e.pay("V", "X1", 100, 0);
        Transaction g2 = e.pay("V", "Y1", 100, 0);
        e.pay("X1", "X2", 100, 1);
        e.pay("Y1", "Y2", 100, 1);
        e.atm("X2", 100, 5);
        e.atm("Y2", 100, 5);
        ChokepointFinder.Result res2 = run(e, List.of(g1, g2));
        assertEquals(2, res2.chokepoints().size());
        assertEquals(2, res2.disjointCashOutRoutes());
    }

    @Test
    void noCashOutYetMeansNoCut() {
        TestData d = new TestData();
        Transaction f = d.pay("V", "M1", 100, 0);
        d.pay("M1", "M2", 100, 1);
        assertTrue(run(d, List.of(f)).chokepoints().isEmpty());
    }
}
