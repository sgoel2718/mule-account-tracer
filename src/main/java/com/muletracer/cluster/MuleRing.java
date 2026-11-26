package com.muletracer.cluster;

import java.util.List;
import java.util.Set;

/**
 * A group of accounts controlled by the same operator, linked through shared devices or networks.
 *
 * @param id               stable ring id (R1, R2, ...)
 * @param members          all accounts in the ring, sorted
 * @param taintedMembers   members that touched the victim's money
 * @param dormantMembers   members not (yet) used in this scam: candidates for a pre-emptive freeze
 * @param sharedSignals    the device ids / ip hashes that tie the ring together
 */
public record MuleRing(String id, List<String> members, Set<String> taintedMembers,
                       List<String> dormantMembers, Set<String> sharedSignals) {
}
