package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.*;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

@ApplicationScoped
public class PromptBuilder {

    private static final String OUTPUT_SCHEMA = """
You are an energy flexibility analytics assistant for a Virtual Power Plant (VPPaaS).
Return a single JSON object using the exact schema and key order below.
No markdown, no code fences, no extra text.

Output schema (keys and structure must match exactly):
{
  "analysis_type": "FLEXIBILITY_FORECASTING",
  "summary": "...",
  "sentiment": "POSITIVE|NEUTRAL|NEGATIVE|MIXED|UNKNOWN",
  "confidence_score": 0.0,
  "solar_impact": "...",
  "key_patterns": [
    {"pattern": "...", "evidence": "...", "impact": "..."}
  ],
  "risks": [
    {"risk": "...", "severity": "LOW|MEDIUM|HIGH", "evidence": "...", "mitigation": "..."}
  ],
  "recommendations": [
    {"action": "...", "rationale": "...", "expected_effect": "..."}
  ],
  "statistics": {
    "total_flexibility_events": 0,
    "correlated_outcomes": 0,
    "success_rate_percent": 0.0,
    "solar_asset_count": 0,
    "avg_solar_generation_kw": 0.0,
    "total_daily_solar_kwh": 0.0,
    "event_type_counts": {"TYPE": 0},
    "recommended_action_counts": {"ACTION": 0},
    "grid_cell_counts": {"CELL": 0}
  }
}

Rules:
- Use the exact keys and structure from the schema.
- Do not add or remove keys.
- If unknown, use null, [] or {} as appropriate.
- Use JSON numbers for numeric values (no quotes).

""";

    public String buildPrompt(EventCorrelationResult correlation) {
        int totalEvents = correlation.totalFlexibilityEvents;
        int correlatedCount = correlation.correlatedOutcomes;
        double successRate = correlation.successRate;
        int solarAssets = correlation.solarAssetCount;
        double avgSolar = correlation.avgCurrentGenerationKw;
        double totalDaily = correlation.totalDailyGenerationKwh;

        Map<String, Integer> eventTypeCounts = new TreeMap<>();
        Map<String, Integer> recommendedActionCounts = new TreeMap<>();
        Map<String, Integer> gridCellCounts = new TreeMap<>();
        double socSum = 0.0;
        int socCount = 0;

        for (FlexibilityEventDTO event : safe(correlation.flexibilityLogs)) {
            incrementCount(eventTypeCounts, normalizeKey(event.eventType));
            incrementCount(recommendedActionCounts, normalizeKey(event.recommendedAction));
            incrementCount(gridCellCounts, normalizeKey(event.gridCellId));
            if (event.soc_percent != null) {
                socSum += event.soc_percent;
                socCount++;
            }
        }

        String avgSoc = socCount > 0 ? String.format(Locale.US, "%.2f", socSum / socCount) : "null";

        StringBuilder prompt = new StringBuilder();
        prompt.append(OUTPUT_SCHEMA);
        prompt.append(buildInputContext(totalEvents, correlatedCount, successRate,
                solarAssets, avgSolar, totalDaily,
                eventTypeCounts, recommendedActionCounts, gridCellCounts, avgSoc,
                correlation.solarGenerationByGridCell));
        prompt.append(buildEventSample(correlation));
        prompt.append(buildSolarSample(correlation));
        prompt.append(buildRecommendationSample(correlation));
        prompt.append("\nReturn only the JSON object described in the schema.");
        return prompt.toString();
    }

    private String buildInputContext(int totalEvents, int correlatedCount, double successRate,
                                     int solarAssets, double avgSolar, double totalDaily,
                                     Map<String, Integer> eventTypeCounts,
                                     Map<String, Integer> recommendedActionCounts,
                                     Map<String, Integer> gridCellCounts,
                                     String avgSoc,
                                     Map<String, Double> solarByGridCell) {
        return String.format(Locale.US, """
Input context:
{
  "analysis_type": "FLEXIBILITY_FORECASTING",
  "totals": {
    "total_flexibility_events": %d,
    "correlated_outcomes": %d,
    "success_rate_percent": %.2f,
    "solar_asset_count": %d,
    "avg_solar_generation_kw": %.2f,
    "total_daily_solar_kwh": %.2f,
    "avg_battery_soc_percent": %s
  },
  "derived_counts": {
    "event_type_counts": %s,
    "recommended_action_counts": %s,
    "grid_cell_counts": %s,
    "solar_generation_by_grid_cell_kw": %s
  }
}

""",
                totalEvents, correlatedCount,
                successRate,
                solarAssets,
                avgSolar,
                totalDaily,
                avgSoc,
                formatCounts(eventTypeCounts),
                formatCounts(recommendedActionCounts),
                formatCounts(gridCellCounts),
                formatDoubleCounts(solarByGridCell != null ? solarByGridCell : Collections.emptyMap()));
    }

    private String buildEventSample(EventCorrelationResult correlation) {
        StringBuilder sample = new StringBuilder("Flexibility event sample (JSON lines, up to 25):\n");
        for (CorrelatedEventEntry cee : safe(correlation.correlatedEvents).stream().limit(25).toList()) {
            FlexibilityEventDTO e = cee.event;
            sample.append(String.format(
                    "{\"timestamp\": %s, \"assetId\": %s, \"prosumerId\": %s, \"eventType\": %s, "
                            + "\"recommendedAction\": %s, \"soc_percent\": %s, \"marketPrice\": %s, "
                            + "\"gridCellId\": %s, \"correlated_outcomes\": %d, \"solar_generation_kw\": %s}\n",
                    jsonString(e.timestamp != null ? e.timestamp.toString() : null),
                    jsonNumber(e.assetId),
                    jsonNumber(e.prosumerId),
                    jsonString(e.eventType),
                    jsonString(e.recommendedAction),
                    jsonNumber(e.soc_percent),
                    jsonNumber(e.marketPrice),
                    jsonString(e.gridCellId),
                    cee.recommendations != null ? cee.recommendations.size() : 0,
                    jsonNumber(cee.solarGenerationKw)));
        }
        return sample.toString();
    }

    private String buildSolarSample(EventCorrelationResult correlation) {
        if (safe(correlation.solarTelemetry).isEmpty()) {
            return "\nSolar telemetry: no data available\n";
        }
        StringBuilder sample = new StringBuilder("\nSolar telemetry sample (JSON lines, up to 10):\n");
        for (SolarTelemetryDTO t : safe(correlation.solarTelemetry).stream().limit(10).toList()) {
            sample.append(String.format(
                    "{\"assetId\": %s, \"gridCellId\": %s, \"currentGeneration_kw\": %s, \"dailyTotal_kwh\": %s, \"timeStamp\": %s}\n",
                    jsonNumber(t.asset_id),
                    jsonString(t.grid_cell_id),
                    jsonNumber(t.Current_Generation),
                    jsonNumber(t.Daily_Total),
                    jsonString(t.timeStamp != null ? t.timeStamp.toString() : null)));
        }
        return sample.toString();
    }

    private String buildRecommendationSample(EventCorrelationResult correlation) {
        if (safe(correlation.gridBalancingLogs).isEmpty()) {
            return "\nGrid balancing recommendations: no data available\n";
        }
        StringBuilder sample = new StringBuilder("\nGrid balancing recommendation sample (JSON lines, up to 25):\n");
        for (BalancingRecommendationDTO rec : safe(correlation.gridBalancingLogs).stream().limit(25).toList()) {
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

    private <T> java.util.List<T> safe(java.util.List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    private void incrementCount(Map<String, Integer> counts, String key) {
        counts.merge(key, 1, Integer::sum);
    }

    private String normalizeKey(String value) {
        return (value == null || value.isBlank()) ? "UNKNOWN" : value;
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String jsonNumber(Number value) {
        return value == null ? "null" : value.toString();
    }

    private String formatCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(jsonString(e.getKey())).append(": ").append(e.getValue());
            first = false;
        }
        return sb.append("}").toString();
    }

    private String formatDoubleCounts(Map<String, Double> counts) {
        if (counts.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : counts.entrySet()) {
            if (!first) sb.append(", ");
            sb.append(jsonString(e.getKey())).append(": ").append(String.format(Locale.US, "%.2f", e.getValue()));
            first = false;
        }
        return sb.append("}").toString();
    }
}
