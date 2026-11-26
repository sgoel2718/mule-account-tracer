package com.muletracer.graph;

import com.muletracer.TestData;
import com.muletracer.model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalGraphTest {

    @Test
    void keepsAdjacencySortedEvenWhenEventsArriveLate() {
        TestData d = new TestData();
        d.pay("A", "B", 100, 30);
        d.pay("A", "C", 100, 10);
        d.pay("A", "D", 100, 20);
        d.pay("A", "E", 100, 40);
        List<Transaction> out = d.graph.outgoing("A");
        for (int i = 1; i < out.size(); i++) {
            assertTrue(out.get(i - 1).timestamp() <= out.get(i).timestamp());
        }
        assertEquals("C", out.get(0).to());
        assertEquals("E", out.get(3).to());
    }

    @Test
    void lowerBoundFindsFirstDebitAtOrAfterTime() {
        TestData d = new TestData();
        d.pay("A", "B", 1, 10);
        d.pay("A", "C", 1, 20);
        d.pay("A", "D", 1, 20);
        d.pay("A", "E", 1, 30);
        assertEquals(0, d.graph.firstOutgoingAtOrAfter("A", 0));
        assertEquals(1, d.graph.firstOutgoingAtOrAfter("A", 20 * 60));
        assertEquals(3, d.graph.firstOutgoingAtOrAfter("A", 20 * 60 + 1));
        assertEquals(4, d.graph.firstOutgoingAtOrAfter("A", 31 * 60));
        assertEquals(0, d.graph.firstOutgoingAtOrAfter("NOBODY", 0));
    }

    @Test
    void rejectsDuplicateTransactionIds() {
        TestData d = new TestData();
        Transaction t = d.pay("A", "B", 1, 1);
        assertThrows(IllegalArgumentException.class, () -> d.graph.addTransaction(t));
    }

    @Test
    void createsPlaceholderAccountsForUnknownCounterparties() {
        TestData d = new TestData();
        d.pay("A", "B", 1, 1);
        assertEquals(2, d.graph.accountCount());
        assertEquals(1, d.graph.incoming("B").size());
    }
}
