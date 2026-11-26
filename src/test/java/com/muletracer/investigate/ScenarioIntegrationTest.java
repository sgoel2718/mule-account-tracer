package com.muletracer.investigate;

import com.muletracer.model.Transaction;
import com.muletracer.sim.ScamScenarioGenerator;
import com.muletracer.sim.ScamScenarioGenerator.Scenario;
import com.muletracer.trace.TraceConfig;
import com.muletracer.trace.TraceResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end checks on the generated "digital arrest" scam (ground truth is known in simulation). */
class ScenarioIntegrationTest {

    private static final Scenario SC = ScamScenarioGenerator.generate(1930, 3_000, 30_000);
    private static final Investigator INV = new Investigator(SC.graph(), TraceConfig.defaults());

    private static long lastFraud() {
        long last = 0;
        for (Transaction t : SC.fraudTxns()) {
            last = Math.max(last, t.timestamp());
        }
        return last;
    }

    @Test
    void generatorIsDeterministic() {
        Scenario again = ScamScenarioGenerator.generate(1930, 3_000, 30_000);
        assertEquals(SC.victimId(), again.victimId());
        assertEquals(SC.graph().transactionCount(), again.graph().transactionCount());
    }

    @Test
    void moneyIsConservedAtEveryDelay() {
        for (int delay : new int[]{0, 15, 60, 240, 480}) {
            TraceResult r = INV.investigate(SC.fraudTxns(), lastFraud() + delay * 60L).trace();
            assertEquals(r.fraudTotal(), r.cashedOut() + r.freezable() + r.beyondHopLimit());
            assertEquals(1_850_000L * 100, r.fraudTotal());
        }
    }

    @Test
    void recoveryFallsAsTheComplaintIsDelayed() {
        double previous = 1.01;
        for (int delay : new int[]{0, 30, 60, 120, 240}) {
            double rate = INV.investigate(SC.fraudTxns(), lastFraud() + delay * 60L).trace().recoveryRate();
            assertTrue(rate <= previous, "recovery should not increase with delay");
            previous = rate;
        }
    }

    @Test
    void neverHardFreezesAnInnocentShop() {
        Investigation inv = INV.investigate(SC.fraudTxns(), lastFraud() + 240 * 60L);
        for (FreezeOrder o : inv.freezeOrders()) {
            if (o.action().equals("FREEZE") || o.action().equals("BLOCK")) {
                assertTrue(SC.activeMules().contains(o.accountId()), o.accountId() + " is not a mule");
            }
        }
        for (String shop : SC.innocentRecipients()) {
            if (inv.scores().containsKey(shop)) {
                assertEquals("LIKELY_INNOCENT", inv.scores().get(shop).verdict(), shop);
            }
        }
    }

    @Test
    void findsEveryActiveMuleAndTheDormantOnes() {
        Investigation inv = INV.investigate(SC.fraudTxns(), lastFraud() + 60 * 60L);
        for (String m : SC.activeMules()) {
            assertTrue(inv.trace().accounts().containsKey(m), "mule not traced: " + m);
        }
        assertTrue(inv.watchlist().containsAll(SC.dormantMules()));
        assertFalse(inv.chokepoints().chokepoints().isEmpty());
    }

    @Test
    void freezeOrdersAreSortedByRecoverableValue() {
        List<FreezeOrder> orders = INV.investigate(SC.fraudTxns(), lastFraud() + 30 * 60L).freezeOrders();
        for (int i = 1; i < orders.size(); i++) {
            double prev = orders.get(i - 1).heldPaise() * orders.get(i - 1).score();
            double cur = orders.get(i).heldPaise() * orders.get(i).score();
            assertTrue(prev >= cur);
        }
    }
}
