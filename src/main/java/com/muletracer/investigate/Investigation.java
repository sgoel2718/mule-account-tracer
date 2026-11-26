package com.muletracer.investigate;

import com.muletracer.cluster.MuleRing;
import com.muletracer.cut.ChokepointFinder;
import com.muletracer.score.MuleScore;
import com.muletracer.trace.TraceResult;

import java.util.List;
import java.util.Map;

/**
 * Everything produced for one fraud complaint.
 *
 * @param trace        money trail
 * @param rings        mule rings touching the trail
 * @param scores       risk score per tainted account
 * @param chokepoints  minimum vertex cut between victim and cash-out points
 * @param freezeOrders prioritised actions (highest recoverable value first)
 * @param watchlist    dormant ring members not used yet: flag before the next victim pays them
 * @param elapsedNanos wall-clock time of the whole investigation
 */
public record Investigation(TraceResult trace, List<MuleRing> rings, Map<String, MuleScore> scores,
                            ChokepointFinder.Result chokepoints, List<FreezeOrder> freezeOrders,
                            List<String> watchlist, long elapsedNanos) {
}
