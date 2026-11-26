package com.muletracer;

import com.muletracer.graph.TemporalGraph;
import com.muletracer.model.Account;
import com.muletracer.model.Channel;
import com.muletracer.model.Transaction;

/** Small hand-built graphs for unit tests. Amounts in rupees are converted to paise. */
public final class TestData {

    private int seq;
    public final TemporalGraph graph = new TemporalGraph();

    public TestData account(String id, int ageDays, String device, String ip) {
        graph.addAccount(new Account(id, "TEST", "Chennai", ageDays, device, ip));
        return this;
    }

    public Transaction pay(String from, String to, long rupees, long minute) {
        return pay(from, to, rupees, minute, Channel.IMPS);
    }

    public Transaction pay(String from, String to, long rupees, long minute, Channel ch) {
        Transaction t = new Transaction(String.format("T%04d", ++seq), from, to, rupees * 100, minute * 60, ch);
        graph.addTransaction(t);
        return t;
    }

    public Transaction atm(String from, long rupees, long minute) {
        return pay(from, "CASH:ATM-TEST", rupees, minute, Channel.ATM_WITHDRAWAL);
    }

    public static long paise(long rupees) {
        return rupees * 100;
    }
}
