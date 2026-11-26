package com.muletracer.trace;

import com.muletracer.TestData;
import com.muletracer.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.muletracer.TestData.paise;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaintTracerTest {

    private static TraceResult trace(TestData d, List<Transaction> fraud, long reportMinute) {
        return new TaintTracer(d.graph, TraceConfig.defaults()).trace(fraud, reportMinute * 60);
    }

    private static void assertConserved(TraceResult r) {
        assertEquals(r.fraudTotal(), r.cashedOut() + r.freezable() + r.beyondHopLimit(),
                "every stolen rupee must be accounted for");
    }

    @Test
    void followsMoneyThroughLayersToCashOut() {
        TestData d = new TestData();
        Transaction f = d.pay("VICTIM", "M1", 1000, 0);
        d.pay("M1", "M2", 600, 5);
        d.pay("M1", "M3", 400, 6);
        d.atm("M2", 600, 20);

        TraceResult r = trace(d, List.of(f), 60);
        assertEquals(paise(1000), r.fraudTotal());
        assertEquals(paise(600), r.cashedOut());
        assertEquals(paise(400), r.freezable());
        assertEquals(paise(400), r.accounts().get("M3").held());
        assertEquals(2, r.accounts().get("M3").hop());
        assertConserved(r);
    }

    @Test
    void moneyCannotTravelBackInTime() {
        TestData d = new TestData();
        d.pay("M1", "SHOP", 500, 0);          // paid BEFORE the victim money arrived
        Transaction f = d.pay("VICTIM", "M1", 1000, 10);

        TraceResult r = trace(d, List.of(f), 60);
        assertFalse(r.accounts().containsKey("SHOP"), "a debit before the fraud must not be blamed");
        assertEquals(paise(1000), r.accounts().get("M1").held());
    }

    @Test
    void taintedFirstRuleCapsAtTaintedBalance() {
        TestData d = new TestData();
        Transaction f = d.pay("VICTIM", "M1", 300, 0);
        d.pay("M1", "B", 1000, 5); // M1 also had own money; only 300 of this is victim money
        d.pay("M1", "C", 200, 6);  // nothing tainted left

        TraceResult r = trace(d, List.of(f), 60);
        assertEquals(paise(300), r.accounts().get("B").received());
        assertFalse(r.accounts().containsKey("C"));
        assertConserved(r);
    }

    @Test
    void reportTimeHidesFutureTransactions() {
        TestData d = new TestData();
        Transaction f = d.pay("VICTIM", "M1", 1000, 0);
        d.pay("M1", "M2", 1000, 30);

        TraceResult early = trace(d, List.of(f), 10);
        assertEquals(paise(1000), early.accounts().get("M1").held());
        assertFalse(early.accounts().containsKey("M2"));

        TraceResult late = trace(d, List.of(f), 40);
        assertEquals(0, late.accounts().get("M1").held());
        assertEquals(paise(1000), late.accounts().get("M2").held());
    }

    @Test
    void multipleFraudTransfersAreReplayedInTimeOrder() {
        TestData d = new TestData();
        Transaction f1 = d.pay("VICTIM", "M1", 100, 0);
        d.pay("M1", "M2", 500, 5);           // only 100 tainted at 5 min, second transfer not yet sent
        Transaction f2 = d.pay("VICTIM", "M1", 400, 10);
        d.pay("M1", "M3", 400, 15);

        TraceResult r = trace(d, List.of(f2, f1), 60); // order of the complaint list must not matter
        assertEquals(paise(100), r.accounts().get("M2").received());
        assertEquals(paise(400), r.accounts().get("M3").received());
        assertConserved(r);
    }

    @Test
    void cyclesDoNotLoopForever() {
        TestData d = new TestData();
        Transaction f = d.pay("VICTIM", "A", 1000, 0);
        d.pay("A", "B", 1000, 1);
        d.pay("B", "A", 1000, 2);
        d.pay("A", "B", 1000, 3);
        d.pay("B", "C", 1000, 4);

        TraceResult r = trace(d, List.of(f), 60);
        assertEquals(paise(1000), r.accounts().get("C").held());
        assertEquals(1, r.accounts().get("A").hop());
        assertConserved(r);
    }

    @Test
    void hopLimitStopsTheTrail() {
        TestData d = new TestData();
        Transaction f = d.pay("VICTIM", "H1", 100, 0);
        d.pay("H1", "H2", 100, 1);
        d.pay("H2", "H3", 100, 2);
        d.pay("H3", "H4", 100, 3);

        TraceResult r = new TaintTracer(d.graph, new TraceConfig(2, 3600)).trace(List.of(f), 3600);
        assertTrue(r.accounts().containsKey("H2"));
        assertFalse(r.accounts().containsKey("H3"));
        assertEquals(paise(100), r.beyondHopLimit());
        assertConserved(r);
    }

    @Test
    void moneyReturnedToVictimCountsAsRecoverable() {
        TestData d = new TestData();
        Transaction f = d.pay("VICTIM", "M1", 1000, 0);
        d.pay("M1", "VICTIM", 200, 5); // "refund" trick to look legitimate
        TraceResult r = trace(d, List.of(f), 60);
        assertEquals(paise(1000), r.freezable());
        assertConserved(r);
    }

    @Test
    void needsAtLeastOneFraudTransaction() {
        TestData d = new TestData();
        assertThrows(IllegalArgumentException.class, () -> trace(d, List.of(), 0));
    }
}
