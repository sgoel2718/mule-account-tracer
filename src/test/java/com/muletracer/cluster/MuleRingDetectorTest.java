package com.muletracer.cluster;

import com.muletracer.TestData;
import com.muletracer.model.Account;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MuleRingDetectorTest {

    @Test
    void linksAccountsThroughSharedDevicesAndNetworksTransitively() {
        TestData d = new TestData()
                .account("M1", 10, "PHONE-1", "NET-1")
                .account("M2", 10, "PHONE-1", "NET-2")   // same phone as M1
                .account("M3", 10, "PHONE-2", "NET-2")   // same network as M2 -> same ring
                .account("DORMANT", 3, "PHONE-2", "NET-9")
                .account("HONEST", 900, "PHONE-H", "NET-H");

        List<MuleRing> rings = new MuleRingDetector(d.graph).detect(Set.of("M1"));
        assertEquals(1, rings.size());
        MuleRing r = rings.get(0);
        assertEquals(List.of("DORMANT", "M1", "M2", "M3"), r.members());
        assertEquals(Set.of("M1"), r.taintedMembers());
        assertTrue(r.dormantMembers().contains("DORMANT"));
        assertTrue(r.sharedSignals().contains("device PHONE-1"));
    }

    @Test
    void ignoresRingsTheTraceNeverTouched() {
        TestData d = new TestData()
                .account("X1", 10, "P", "N")
                .account("X2", 10, "P", "N")
                .account("LONE", 10, "Q", "R");
        assertTrue(new MuleRingDetector(d.graph).detect(Set.of("LONE")).isEmpty());
    }

    @Test
    void incrementalRegistrationMergesRings() {
        TestData d = new TestData()
                .account("A", 10, "P1", "N1")
                .account("B", 10, "P2", "N2");
        MuleRingDetector det = new MuleRingDetector(d.graph);
        assertTrue(det.detect(Set.of("A")).isEmpty());
        det.register(new Account("BRIDGE", "T", "C", 1, "P1", "N2")); // new login links A and B
        assertEquals(3, det.detect(Set.of("A")).get(0).members().size());
    }
}
