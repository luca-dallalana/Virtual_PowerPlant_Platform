package org.acme.services;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.dto.FlexibilityEventDTO;
import org.acme.dto.BalancingRecommendationDTO;
import org.acme.dto.ForecastRequest;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PromptBuilder {

    public String buildPrompt(ForecastRequest request,
                            List<FlexibilityEventDTO> events,
                            Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {

        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an energy grid AI analyst specialized in Virtual Power Plants.\n\n");

        String analysisType = request.analysisType != null ? request.analysisType.toUpperCase() : "GENERAL";

        switch (analysisType) {
            case "SENTIMENT":
                prompt.append(buildSentimentPrompt(events, correlations));
                break;
            case "SUCCESS_RATE":
                prompt.append(buildSuccessRatePrompt(request, events, correlations));
                break;
            case "CORRELATION":
                prompt.append(buildCorrelationPrompt(request, events, correlations));
                break;
            default:
                prompt.append(buildGeneralPrompt(request, events, correlations));
        }

        return prompt.toString();
    }

    private String buildSentimentPrompt(List<FlexibilityEventDTO> events,
                                       Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: Analyze the overall sentiment of flexibility operations.\n\n");
        prompt.append("Historical Flexibility Events:\n");

        for (FlexibilityEventDTO event : events.stream().limit(50).toList()) {
            prompt.append(String.format("- %s | Asset %d | %s → %s | SoC: %.1f%% | Grid: %s\n",
                    event.timestamp, event.assetId, event.eventType,
                    event.recommendedAction, event.soc_percent, event.gridCellId));
        }

        prompt.append("\nProvide:\n");
        prompt.append("1. Overall sentiment (POSITIVE/NEGATIVE/NEUTRAL)\n");
        prompt.append("2. Confidence score (0-100%)\n");
        prompt.append("3. Key insights (2-3 bullet points)\n\n");
        prompt.append("Format as JSON:\n");
        prompt.append("{\n  \"sentiment\": \"POSITIVE/NEGATIVE/NEUTRAL\",\n  \"confidence\": 0-100,\n  \"insights\": [\"...\", \"...\"]\n}");

        return prompt.toString();
    }

    private String buildSuccessRatePrompt(ForecastRequest request,
                                         List<FlexibilityEventDTO> events,
                                         Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        StringBuilder prompt = new StringBuilder();
        String targetAction = request.recommendedAction != null ? request.recommendedAction : "all actions";

        prompt.append(String.format("Task: Determine the success rate of %s in achieving grid stability.\n\n", targetAction));
        prompt.append("Historical Flexibility Events with Outcomes:\n");

        int eventCount = 0;
        for (Map.Entry<FlexibilityEventDTO, List<BalancingRecommendationDTO>> entry : correlations.entrySet()) {
            if (eventCount >= 30) break;
            FlexibilityEventDTO event = entry.getKey();
            List<BalancingRecommendationDTO> outcomes = entry.getValue();

            if (request.recommendedAction != null &&
                !request.recommendedAction.equals(event.recommendedAction)) {
                continue;
            }

            prompt.append(String.format("[Event %d]: %s | Asset %d | %s → %s | SoC: %.1f%% | Grid: %s\n",
                    eventCount + 1, event.timestamp, event.assetId, event.eventType,
                    event.recommendedAction, event.soc_percent, event.gridCellId));

            if (!outcomes.isEmpty()) {
                for (BalancingRecommendationDTO outcome : outcomes) {
                    prompt.append(String.format("  Outcome: %s | Status: %s | Overload: %.1fkW | Rationale: %s\n",
                            outcome.createdAt, outcome.status, outcome.overloadKw, outcome.rationale));
                }
            } else {
                prompt.append("  Outcome: No correlated grid balancing data\n");
            }
            eventCount++;
        }

        String customQuestion = request.customQuestion != null ? request.customQuestion :
                String.format("Did the %s commands result in grid stability?", targetAction);

        prompt.append(String.format("\nAnalysis Question: %s\n\n", customQuestion));
        prompt.append("Provide:\n");
        prompt.append("1. Success determination (YES/NO)\n");
        prompt.append("2. Confidence score (0-100%)\n");
        prompt.append("3. Key insights (2-3 bullet points)\n");
        prompt.append("4. Success rate percentage if multiple events analyzed\n\n");
        prompt.append("Format as JSON:\n");
        prompt.append("{\n  \"success\": true/false,\n  \"confidence\": 0-100,\n  \"insights\": [\"...\", \"...\"],\n  \"success_rate\": 0-100\n}");

        return prompt.toString();
    }

    private String buildCorrelationPrompt(ForecastRequest request,
                                         List<FlexibilityEventDTO> events,
                                         Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Task: Analyze the correlation between flexibility events and grid balancing outcomes.\n\n");

        prompt.append("Flexibility Events:\n");
        for (FlexibilityEventDTO event : events.stream().limit(20).toList()) {
            prompt.append(String.format("- %s | Asset %d | %s → %s | Grid: %s\n",
                    event.timestamp, event.assetId, event.eventType,
                    event.recommendedAction, event.gridCellId));
        }

        prompt.append("\nCorrelation Summary:\n");
        long eventsWithOutcomes = correlations.entrySet().stream()
                .filter(e -> !e.getValue().isEmpty())
                .count();
        prompt.append(String.format("- Total events: %d\n", events.size()));
        prompt.append(String.format("- Events with correlated outcomes: %d\n", eventsWithOutcomes));

        String customQuestion = request.customQuestion != null ? request.customQuestion :
                "What patterns emerge from the correlation between flexibility events and grid stability?";

        prompt.append(String.format("\nAnalysis Question: %s\n\n", customQuestion));
        prompt.append("Provide insights on correlation patterns and their implications.\n\n");
        prompt.append("Format as JSON:\n");
        prompt.append("{\n  \"correlation_strength\": \"STRONG/MODERATE/WEAK\",\n  \"confidence\": 0-100,\n  \"insights\": [\"...\", \"...\"]\n}");

        return prompt.toString();
    }

    private String buildGeneralPrompt(ForecastRequest request,
                                     List<FlexibilityEventDTO> events,
                                     Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        StringBuilder prompt = new StringBuilder();
        String customQuestion = request.customQuestion != null ? request.customQuestion :
                "Analyze the flexibility events and their impact on grid stability.";

        prompt.append(String.format("Task: %s\n\n", customQuestion));
        prompt.append("Historical Flexibility Events:\n");

        for (FlexibilityEventDTO event : events.stream().limit(30).toList()) {
            prompt.append(String.format("- %s | Asset %d | %s → %s | SoC: %.1f%% | Grid: %s\n",
                    event.timestamp, event.assetId, event.eventType,
                    event.recommendedAction, event.soc_percent, event.gridCellId));
        }

        prompt.append("\nProvide a comprehensive analysis with key insights.\n\n");
        prompt.append("Format as JSON:\n");
        prompt.append("{\n  \"summary\": \"...\",\n  \"insights\": [\"...\", \"...\"],\n  \"confidence\": 0-100\n}");

        return prompt.toString();
    }
}
