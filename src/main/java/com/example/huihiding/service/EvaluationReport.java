package com.example.huihiding.service;

import java.util.Set;

/**
 * DTO for UI/consumer layers to display end-to-end evaluation results.
 */
public record EvaluationReport(
        String originalTransactions,
        String sanitizedTransactions,
        Set<String> originalHUIs,
        Set<String> sanitizedHUIs,
        double hfPercentage,
        double mcPercentage,
        double acPercentage,
        long runtimeMs
) {
}
