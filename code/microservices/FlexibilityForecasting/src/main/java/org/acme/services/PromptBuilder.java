package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.BalancingRecommendationDTO;
import org.acme.dto.FlexibilityEventDTO;
import org.acme.dto.ForecastRequest;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class PromptBuilder {

    private static final String OUTPUT_SCHEMA = """
You are an energy flexibility analytics assistant.
Return a single JSON object using the exact schema and key order below.
No markdown, no code fences, no extra text.

Output schema (keys and structure must match exactly):
{
  "analysis_type": "...",
  "question": "...",
  "summary": "...",
  "sentiment": "POSITIVE|NEUTRAL|NEGATIVE|MIXED|UNKNOWN",
  "confidence_score": 0.0,
  "key_patterns": [
    {"pattern": "...", "evidence": "...", "impact": "..."}
  ],
  "risks": [
    {"risk": "...", "severity": "LOW|MEDIUM|HIGH", "evidence": "...", "mitigation": "..."}
  ],
  "recommendations": [
    {"action": "...", "rationale": "...", "expected_effect": "..."}
  ],
  "data_quality_notes": [
    {"note": "...", "impact": "..."}
  ],
  "statistics": {
    "total_events": 0,
    "correlated_outcomes": 0,
    "event_type_counts": {"TYPE": 0},
    "recommended_action_counts": {"ACTION": 0},
    "grid_cell_counts": {"CELL": 0},
    "avg_soc_percent": 0.0,
    "avg_market_price": 0.0,
    "avg_incentive_amount": 0.0
  }
}

Rules:
- Use the exact keys and structure from the schema.
- Do not add or remove keys.
- If unknown, use null, [] or {} as appropriate.
- Use JSON numbers for numeric values (no quotes).

""";

    public String buildPrompt(ForecastRequest request,
                              List<FlexibilityEventDTO> events,
                              Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        String analysisType = request.analysisType != null ? request.analysisType : "GENERAL";
        String question = request.customQuestion != null ? request.customQuestion :
                "Analyze flexibility events and related outcomes.";
        String eventTypeFilter = request.eventType != null ? request.eventType : "ANY";
        String assetIdFilter = request.assetId != null ? request.assetId.toString() : "ANY";
        String recommendedActionFilter = request.recommendedAction != null ? request.recommendedAction : "ANY";
        String startDateFilter = request.startDate != null ? request.startDate.toString() : "UNSPECIFIED";
        String endDateFilter = request.endDate != null ? request.endDate.toString() : "UNSPECIFIED";

        int totalEvents = events.size();
        int correlatedCount = (int) correlations.values().stream()
                .mapToLong(List::size)
                .sum();

        Map<String, Integer> eventTypeCounts = new TreeMap<>();
        Map<String, Integer> recommendedActionCounts = new TreeMap<>();
        Map<String, Integer> gridCellCounts = new TreeMap<>();
        double socSum = 0.0;
        int socCount = 0;
        double marketPriceSum = 0.0;
        int marketPriceCount = 0;
        double incentiveSum = 0.0;
        int incentiveCount = 0;

        for (FlexibilityEventDTO event : events) {
            incrementCount(eventTypeCounts, normalizeKey(event.eventType));
            incrementCount(recommendedActionCounts, normalizeKey(event.recommendedAction));
            incrementCount(gridCellCounts, normalizeKey(event.gridCellId));

            if (event.soc_percent != null) {
                socSum += event.soc_percent;
                socCount++;
            }
            if (event.marketPrice != null) {
                marketPriceSum += event.marketPrice;
                marketPriceCount++;
            }
            if (event.incentiveAmount != null) {
                incentiveSum += event.incentiveAmount;
                incentiveCount++;
            }
        }

        String avgSoc = socCount > 0 ? String.format(Locale.US, "%.2f", socSum / socCount) : "null";
        String avgMarketPrice = marketPriceCount > 0
                ? String.format(Locale.US, "%.2f", marketPriceSum / marketPriceCount)
                : "null";
        String avgIncentive = incentiveCount > 0
                ? String.format(Locale.US, "%.2f", incentiveSum / incentiveCount)
                : "null";

        StringBuilder prompt = new StringBuilder();
        prompt.append(OUTPUT_SCHEMA);
        prompt.append(buildInputContext(analysisType, question, eventTypeFilter, assetIdFilter,
                recommendedActionFilter, startDateFilter, endDateFilter, totalEvents, correlatedCount,
                eventTypeCounts, recommendedActionCounts, gridCellCounts, avgSoc, avgMarketPrice, avgIncentive));
        prompt.append(buildEventSample(events, correlations));
        prompt.append(buildRecommendationSample(correlations));
        prompt.append("\nReturn only the JSON object described in the schema.");
        return prompt.toString();
    }

    private String buildInputContext(String analysisType,
                                     String question,
                                     String eventTypeFilter,
                                     String assetIdFilter,
                                     String recommendedActionFilter,
                                     String startDateFilter,
                                     String endDateFilter,
                                     int totalEvents,
                                     int correlatedCount,
                                     Map<String, Integer> eventTypeCounts,
                                     Map<String, Integer> recommendedActionCounts,
                                     Map<String, Integer> gridCellCounts,
                                     String avgSoc,
                                     String avgMarketPrice,
                                     String avgIncentive) {
        return String.format("""
Input context:
{
  "analysis_type": %s,
  "question": %s,
  "filters": {
    "event_type": %s,
    "asset_id": %s,
    "recommended_action": %s,
    "start_date": %s,
    "end_date": %s
  },
  "totals": {
    "total_events": %d,
    "correlated_outcomes": %d
  },
  "derived_counts": {
    "event_type_counts": %s,
    "recommended_action_counts": %s,
    "grid_cell_counts": %s
  },
  "averages": {
    "avg_soc_percent": %s,
    "avg_market_price": %s,
    "avg_incentive_amount": %s
  }
}

""",
                jsonString(analysisType),
                jsonString(question),
                jsonString(eventTypeFilter),
                jsonString(assetIdFilter),
                jsonString(recommendedActionFilter),
                jsonString(startDateFilter),
                jsonString(endDateFilter),
                totalEvents,
                correlatedCount,
                formatCounts(eventTypeCounts),
                formatCounts(recommendedActionCounts),
                formatCounts(gridCellCounts),
                avgSoc,
                avgMarketPrice,
                avgIncentive);
    }

    private String buildEventSample(List<FlexibilityEventDTO> events,
                                    Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        StringBuilder sample = new StringBuilder("Event sample (JSON lines, up to 25):\n");
        for (FlexibilityEventDTO event : events.stream().limit(25).toList()) {
            int correlatedForEvent = correlations.getOrDefault(event, Collections.emptyList()).size();
            sample.append(String.format(
                    "{\"timestamp\": %s, \"assetId\": %s, \"prosumerId\": %s, \"eventType\": %s, "
                            + "\"recommendedAction\": %s, \"soc_percent\": %s, \"marketPrice\": %s, "
                            + "\"incentiveAmount\": %s, \"gridCellId\": %s, \"correlated_outcomes\": %d}\n",
                    jsonString(event.timestamp != null ? event.timestamp.toString() : null),
                    jsonNumber(event.assetId),
                    jsonNumber(event.prosumerId),
                    jsonString(event.eventType),
                    jsonString(event.recommendedAction),
                    jsonNumber(event.soc_percent),
                    jsonNumber(event.marketPrice),
                    jsonNumber(event.incentiveAmount),
                    jsonString(event.gridCellId),
                    correlatedForEvent));
        }
        return sample.toString();
    }

    private String buildRecommendationSample(Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        List<BalancingRecommendationDTO> recSample = correlations.values().stream()
                .flatMap(List::stream)
                .limit(25)
                .toList();
        StringBuilder sample = new StringBuilder("\nRecommendation sample (JSON lines, up to 25):\n");
        for (BalancingRecommendationDTO rec : recSample) {
            sample.append(String.format(
                    "{\"createdAt\": %s, \"sourceGridCellId\": %s, \"targetGridCellId\": %s, "
                            + "\"sourceNetLoadKw\": %s, \"targetHeadroomKw\": %s, \"overloadKw\": %s, "
                            + "\"status\": %s, \"rationale\": %s}\n",
                    jsonString(rec.createdAt != null ? rec.createdAt.toString() : null),
                    jsonString(rec.sourceGridCellId),
                    jsonString(rec.targetGridCellId),
                    jsonNumber(rec.sourceNetLoadKw),
                    jsonNumber(rec.targetHeadroomKw),
                    jsonNumber(rec.overloadKw),
                    jsonString(rec.status),
                    jsonString(rec.rationale)));
        }
        return sample.toString();
    }

    private void incrementCount(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value;
    }

    private String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escapeJson(value) + "\"";
    }

    private String jsonNumber(Number value) {
        return value == null ? "null" : value.toString();
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String formatCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "{}";
        }
        StringBuilder formatted = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                formatted.append(", ");
            }
            formatted.append(jsonString(entry.getKey())).append(": ").append(entry.getValue());
            first = false;
        }
        formatted.append("}");
        return formatted.toString();
    }
}
