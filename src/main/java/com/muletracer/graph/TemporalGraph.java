package com.muletracer.graph;

import com.muletracer.model.Account;
import com.muletracer.model.Transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Directed multigraph of transactions where every account keeps its outgoing and
 * incoming edges sorted by timestamp.
 *
 * <p>Why time-sorted adjacency lists? Money can only move forward in time: if Rs 5L
 * lands in account A at 10:04, only A's debits <b>after</b> 10:04 can carry it on.
 * Sorted lists let us jump to the first eligible debit with a binary search
 * ({@link #firstOutgoingAtOrAfter}) instead of scanning A's whole history.</p>
 *
 * <ul>
 *   <li>{@code addTransaction}: amortised O(1) for in-order streams (append), O(d) worst case for late arrivals</li>
 *   <li>{@code firstOutgoingAtOrAfter}: O(log d) where d = out-degree</li>
 * </ul>
 */
public final class TemporalGraph {

    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, List<Transaction>> outgoing = new HashMap<>();
    private final Map<String, List<Transaction>> incoming = new HashMap<>();
    private final Map<String, Transaction> byId = new HashMap<>();

    public void addAccount(Account account) {
        accounts.put(account.id(), account);
    }

    public void addTransaction(Transaction t) {
        if (byId.putIfAbsent(t.id(), t) != null) {
            throw new IllegalArgumentException("duplicate transaction id " + t.id());
        }
        accounts.computeIfAbsent(t.from(), Account::unknown);
        accounts.computeIfAbsent(t.to(), Account::unknown);
        insertSorted(outgoing.computeIfAbsent(t.from(), k -> new ArrayList<>()), t);
        insertSorted(incoming.computeIfAbsent(t.to(), k -> new ArrayList<>()), t);
    }

    /** Keeps the list ordered by (timestamp, id). Streams are nearly sorted, so this is usually an append. */
    private static void insertSorted(List<Transaction> list, Transaction t) {
        int n = list.size();
        if (n == 0 || compare(list.get(n - 1), t) <= 0) {
            list.add(t);
            return;
        }
        int lo = 0;
        int hi = n;
        while (lo < hi) { // upper bound
            int mid = (lo + hi) >>> 1;
            if (compare(list.get(mid), t) <= 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        list.add(lo, t);
    }

    private static int compare(Transaction a, Transaction b) {
        int c = Long.compare(a.timestamp(), b.timestamp());
        return c != 0 ? c : a.id().compareTo(b.id());
    }

    /**
     * Index of the first outgoing transaction of {@code accountId} with timestamp >= {@code ts}
     * (lower-bound binary search). Returns the out-degree when there is none.
     */
    public int firstOutgoingAtOrAfter(String accountId, long ts) {
        List<Transaction> list = outgoing(accountId);
        int lo = 0;
        int hi = list.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (list.get(mid).timestamp() < ts) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    public List<Transaction> outgoing(String accountId) {
        List<Transaction> l = outgoing.get(accountId);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    public List<Transaction> incoming(String accountId) {
        List<Transaction> l = incoming.get(accountId);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(l);
    }

    public Account account(String id) {
        return accounts.get(id);
    }

    public Transaction transaction(String id) {
        return byId.get(id);
    }

    public Collection<Account> accounts() {
        return Collections.unmodifiableCollection(accounts.values());
    }

    public Collection<Transaction> transactions() {
        return Collections.unmodifiableCollection(byId.values());
    }

    public int accountCount() {
        return accounts.size();
    }

    public int transactionCount() {
        return byId.size();
    }
}
