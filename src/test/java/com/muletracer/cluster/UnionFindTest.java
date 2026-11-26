package com.muletracer.cluster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnionFindTest {

    @Test
    void unionsAndFindsComponents() {
        UnionFind uf = new UnionFind();
        for (String k : new String[]{"a", "b", "c", "d", "e"}) {
            uf.add(k);
        }
        assertEquals(5, uf.components());
        assertTrue(uf.union("a", "b"));
        assertTrue(uf.union("c", "d"));
        assertTrue(uf.union("b", "d"));
        assertFalse(uf.union("a", "c"), "already connected");
        assertTrue(uf.connected("a", "d"));
        assertFalse(uf.connected("a", "e"));
        assertEquals(2, uf.components());
        assertEquals(2, uf.groups().size());
    }

    @Test
    void growsBeyondInitialCapacityAndStaysShallow() {
        UnionFind uf = new UnionFind();
        int n = 100_000;
        for (int i = 1; i < n; i++) {
            uf.union("k" + (i - 1), "k" + i); // worst case chain without union by rank
        }
        assertEquals(1, uf.components());
        assertEquals(n, uf.size());
        assertEquals(uf.find("k0"), uf.find("k" + (n - 1)));
    }
}
