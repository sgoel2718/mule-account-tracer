package com.muletracer.score;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRankTest {

    @Test
    void aggregatorGetsHighestRank() {
        PageRank pr = new PageRank();
        pr.addEdge("a", "hub", 1);
        pr.addEdge("b", "hub", 1);
        pr.addEdge("c", "hub", 1);
        pr.addEdge("hub", "d", 1);
        Map<String, Double> r = pr.compute(0.85, 50);
        assertTrue(r.get("hub") > r.get("a"));
        assertTrue(r.get("hub") > r.get("b"));
        assertEquals(1.0, Math.max(r.get("hub"), r.get("d")), 1e-9);
    }

    @Test
    void weightsMatter() {
        PageRank pr = new PageRank();
        pr.addEdge("s", "big", 99);
        pr.addEdge("s", "small", 1);
        Map<String, Double> r = pr.compute(0.85, 50);
        assertTrue(r.get("big") > r.get("small"));
    }
}
