package com.flowaid.service;

import com.flowaid.model.Recipient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Rules-based eligibility scoring for program enrollment.
 *
 * Produces a 0-100 vulnerabilityScore from three weighted factors — income,
 * geographic zone, and household size — plus a decision (ELIGIBLE /
 * NEEDS_REVIEW / INELIGIBLE) with a human-readable reason trail. This
 * replaces manually typing a "vulnerability score" into a form field with an
 * auditable, reproducible calculation: the same inputs always produce the
 * same score, and every score can be explained by which rule contributed it.
 *
 * Each rule is intentionally a small, independent function so new criteria
 * (e.g. disability status, displacement status) can be added without
 * touching the others — the classic "rules engine" shape.
 */
@Slf4j
@Service
public class EligibilityEngine {

    @Value("${flowaid.eligibility.income-threshold-usd:150}")
    private BigDecimal incomeThresholdUsd;

    @Value("${flowaid.eligibility.eligible-score-min:60}")
    private int eligibleScoreMin;

    @Value("${flowaid.eligibility.review-score-min:35}")
    private int reviewScoreMin;

    // Zones with elevated need (conflict, drought, displacement camps, etc).
    // In production this would be sourced from a crisis-monitoring feed
    // rather than hardcoded; kept as config here for transparency/testability.
    @Value("#{'${flowaid.eligibility.high-need-zones:Borno,Turkana,Cabo Delgado,Tigray,Kasai}'.split(',')}")
    private Set<String> highNeedZones;

    public record EligibilityResult(int score, Recipient.EligibilityDecision decision, String reason) {}

    public EligibilityResult evaluate(Recipient recipient) {
        List<String> reasons = new ArrayList<>();
        int score = 0;

        score += scoreIncome(recipient, reasons);
        score += scoreGeographicZone(recipient, reasons);
        score += scoreHouseholdSize(recipient, reasons);
        score = Math.min(100, score);

        Recipient.EligibilityDecision decision;
        if (score >= eligibleScoreMin) {
            decision = Recipient.EligibilityDecision.ELIGIBLE;
        } else if (score >= reviewScoreMin) {
            decision = Recipient.EligibilityDecision.NEEDS_REVIEW;
        } else {
            decision = Recipient.EligibilityDecision.INELIGIBLE;
        }

        String reason = String.join("; ", reasons);
        log.info("Eligibility scored {} for recipient {} -> {} ({})",
                score, recipient.getPhoneNumber(), decision, reason);
        return new EligibilityResult(score, decision, reason);
    }

    // Weight: up to 50 points. Below the poverty-line threshold scores highest;
    // no income data supplied scores a conservative middle value pending review.
    private int scoreIncome(Recipient recipient, List<String> reasons) {
        BigDecimal income = recipient.getMonthlyIncomeUsd();
        if (income == null) {
            reasons.add("no income data supplied (neutral 20pts, flagged for review)");
            return 20;
        }
        if (income.compareTo(BigDecimal.ZERO) <= 0) {
            reasons.add("no reported income (+50pts)");
            return 50;
        }
        BigDecimal ratio = income.divide(incomeThresholdUsd, 4, java.math.RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) <= 0) {
            reasons.add("income at/below threshold of $" + incomeThresholdUsd + " (+50pts)");
            return 50;
        }
        if (ratio.compareTo(BigDecimal.valueOf(1.5)) <= 0) {
            reasons.add("income modestly above threshold (+25pts)");
            return 25;
        }
        reasons.add("income comfortably above threshold (+0pts)");
        return 0;
    }

    // Weight: up to 30 points for recipients located in a designated high-need zone.
    private int scoreGeographicZone(Recipient recipient, List<String> reasons) {
        String region = recipient.getRegion();
        if (region != null && highNeedZones.stream().anyMatch(z -> z.trim().equalsIgnoreCase(region.trim()))) {
            reasons.add("region '" + region + "' is a designated high-need zone (+30pts)");
            return 30;
        }
        reasons.add("region not in current high-need zone list (+0pts)");
        return 0;
    }

    // Weight: up to 20 points, scaling with dependents.
    private int scoreHouseholdSize(Recipient recipient, List<String> reasons) {
        Integer size = recipient.getHouseholdSize();
        if (size == null || size <= 1) {
            reasons.add("household size 1 or unspecified (+0pts)");
            return 0;
        }
        int points = Math.min(20, (size - 1) * 4);
        reasons.add("household size " + size + " (+" + points + "pts)");
        return points;
    }
}
