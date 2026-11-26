package com.muletracer.score;

import java.util.List;
import java.util.Map;

/**
 * Explainable mule risk score.
 *
 * @param accountId account id
 * @param score     0-100; 70+ = likely mule, 40-69 = review, below 40 = likely innocent recipient
 * @param reasons   human-readable reasons an investigator can put in the freeze request
 * @param features  raw feature values (0..1) behind the score
 */
public record MuleScore(String accountId, int score, List<String> reasons, Map<String, Double> features) {

    public String verdict() {
        if (score >= 70) {
            return "LIKELY_MULE";
        }
        if (score >= 40) {
            return "REVIEW";
        }
        return "LIKELY_INNOCENT";
    }
}
