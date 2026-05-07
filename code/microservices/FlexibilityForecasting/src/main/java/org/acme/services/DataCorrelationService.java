package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.FlexibilityEventDTO;
import org.acme.dto.BalancingRecommendationDTO;
import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class DataCorrelationService {

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

    public int countCorrelatedOutcomes(Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        return (int) correlations.values().stream()
                .mapToLong(List::size)
                .sum();
    }
}
