package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.*;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class DataCorrelationService {

    private static final int DEFAULT_CORRELATION_WINDOW_MINUTES = 30;

    public EventCorrelationResult buildResult(EventCorrelationRequest request) {
        List<FlexibilityEventDTO> events = request.flexibilityLogs != null ? request.flexibilityLogs : Collections.emptyList();
        List<BalancingRecommendationDTO> recommendations = request.gridBalancingLogs != null ? request.gridBalancingLogs : Collections.emptyList();
        List<SolarAssetDTO> solarAssets = request.solarAssets != null ? request.solarAssets : Collections.emptyList();
        List<SolarTelemetryDTO> solarTelemetry = request.solarTelemetry != null ? request.solarTelemetry : Collections.emptyList();

        Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations =
                correlateEventsWithOutcomes(events, recommendations, DEFAULT_CORRELATION_WINDOW_MINUTES);

        Map<String, Double> solarByGridCell = aggregateSolarByGridCell(solarTelemetry);
        double totalGeneration = solarTelemetry.stream()
                .filter(t -> t.Current_Generation != null)
                .mapToDouble(t -> t.Current_Generation)
                .sum();
        double totalDaily = solarTelemetry.stream()
                .filter(t -> t.Daily_Total != null)
                .mapToDouble(t -> t.Daily_Total)
                .sum();
        long nonNullGenerationCount = solarTelemetry.stream().filter(t -> t.Current_Generation != null).count();
        double avgGeneration = nonNullGenerationCount > 0 ? totalGeneration / nonNullGenerationCount : 0.0;

        List<CorrelatedEventEntry> correlatedEvents = new ArrayList<>();
        for (Map.Entry<FlexibilityEventDTO, List<BalancingRecommendationDTO>> entry : correlations.entrySet()) {
            CorrelatedEventEntry cee = new CorrelatedEventEntry();
            cee.event = entry.getKey();
            cee.recommendations = entry.getValue();
            if (entry.getKey().gridCellId != null) {
                cee.solarGenerationKw = solarByGridCell.get(entry.getKey().gridCellId);
            }
            correlatedEvents.add(cee);
        }

        EventCorrelationResult result = new EventCorrelationResult();
        result.flexibilityLogs = events;
        result.gridBalancingLogs = recommendations;
        result.solarAssets = solarAssets;
        result.solarTelemetry = solarTelemetry;
        result.totalFlexibilityEvents = events.size();
        result.totalGridBalancingLogs = recommendations.size();
        result.correlatedOutcomes = countCorrelatedOutcomes(correlations);
        result.successRate = calculateSuccessRate(correlations, null);
        result.solarAssetCount = solarAssets.size();
        result.avgCurrentGenerationKw = avgGeneration;
        result.totalDailyGenerationKwh = totalDaily;
        result.solarGenerationByGridCell = solarByGridCell;
        result.correlatedEvents = correlatedEvents;

        return result;
    }

    public Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlateEventsWithOutcomes(
            List<FlexibilityEventDTO> events,
            List<BalancingRecommendationDTO> recommendations,
            int windowMinutes) {

        Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations = new HashMap<>();

        for (FlexibilityEventDTO event : events) {
            List<BalancingRecommendationDTO> relatedRecs = recommendations.stream()
                .filter(rec -> matchesGridCell(event, rec))
                .filter(rec -> isWithinTimeWindow(event.timestamp, rec.createdAt, windowMinutes))
                .sorted(Comparator.comparing(r -> r.createdAt))
                .toList();

            correlations.put(event, relatedRecs);
        }

        return correlations;
    }

    public double calculateSuccessRate(Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations,
                                       String targetAction) {
        long totalActionsOfType = correlations.keySet().stream()
                .filter(event -> targetAction == null || targetAction.equals(event.recommendedAction))
                .count();

        long successfulActions = correlations.entrySet().stream()
                .filter(entry -> targetAction == null || targetAction.equals(entry.getKey().recommendedAction))
                .filter(entry -> isSuccessfulOutcome(entry.getValue()))
                .count();

        return totalActionsOfType > 0 ? (double) successfulActions / totalActionsOfType * 100.0 : 0.0;
    }

    public int countCorrelatedOutcomes(Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        return (int) correlations.values().stream()
                .mapToLong(List::size)
                .sum();
    }

    private Map<String, Double> aggregateSolarByGridCell(List<SolarTelemetryDTO> solarTelemetry) {
        Map<String, Double> byGridCell = new HashMap<>();
        for (SolarTelemetryDTO t : solarTelemetry) {
            if (t.grid_cell_id != null && t.Current_Generation != null) {
                byGridCell.merge(t.grid_cell_id, (double) t.Current_Generation, Double::sum);
            }
        }
        return byGridCell;
    }

    private boolean isSuccessfulOutcome(List<BalancingRecommendationDTO> recommendations) {
        if (recommendations.isEmpty()) {
            return false;
        }
        for (BalancingRecommendationDTO rec : recommendations) {
            if (rec.status != null &&
                (rec.status.contains("BALANCED") ||
                 rec.status.contains("STABLE") ||
                 rec.status.contains("IMPROVED"))) {
                return true;
            }
            if (rec.overloadKw != null && rec.overloadKw <= 0) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesGridCell(FlexibilityEventDTO event, BalancingRecommendationDTO rec) {
        return event.gridCellId != null &&
               (event.gridCellId.equals(rec.sourceGridCellId) ||
                event.gridCellId.equals(rec.targetGridCellId));
    }

    private boolean isWithinTimeWindow(LocalDateTime eventTime, LocalDateTime recTime, int minutes) {
        if (eventTime == null || recTime == null) {
            return false;
        }
        return recTime.isAfter(eventTime) &&
               recTime.isBefore(eventTime.plusMinutes(minutes));
    }
}
