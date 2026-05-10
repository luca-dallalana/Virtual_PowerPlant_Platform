package org.acme.services;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.clients.OllamaService;
import org.acme.dto.*;
import org.acme.entities.FlexibilityForecast;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

@ApplicationScoped
public class ForecastingService {

    @Inject
    MySQLPool client;

    @Inject
    @RestClient
    OllamaService ollamaService;

    @Inject
    DataCorrelationService correlationService;

    @Inject
    PromptBuilder promptBuilder;

    @ConfigProperty(name = "ollama.model", defaultValue = "llama3.2:latest")
    String ollamaModel;

    @ConfigProperty(name = "ollama.temperature", defaultValue = "0.2")
    double ollamaTemperature;

    @ConfigProperty(name = "ollama.max-tokens", defaultValue = "10000")
    int ollamaMaxTokens;

    @ConfigProperty(name = "forecast.max-events", defaultValue = "200")
    int maxEvents;

    @ConfigProperty(name = "forecast.correlation-window-minutes", defaultValue = "30")
    int correlationWindowMinutes;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Uni<ForecastResult> performAnalysis(ForecastRequest request) {
        List<FlexibilityEventDTO> events = request.events != null ? request.events : Collections.emptyList();
        List<BalancingRecommendationDTO> recommendations = request.recommendations != null ? request.recommendations : Collections.emptyList();

        if (events.isEmpty()) {
            return Uni.createFrom().item(createEmptyResult(request));
        }

        Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations =
                correlationService.correlateEventsWithOutcomes(events, recommendations, correlationWindowMinutes);

        String prompt = promptBuilder.buildPrompt(request, events, correlations);

        return callOllama(prompt)
                .onItem().transformToUni(ollamaResponse ->
                        buildAndPersistResult(request, ollamaResponse, events, correlations))
                .onFailure().recoverWithItem(throwable ->
                        createErrorResult(request, events, correlations, throwable.getMessage()));
    }

    private Uni<OllamaResponse> callOllama(String prompt) {
        OllamaRequest request = new OllamaRequest();
        request.model = ollamaModel;
        request.prompt = prompt;
        request.stream = false;
        request.options = new HashMap<>();
        request.options.put("temperature", ollamaTemperature);
        request.options.put("num_predict", ollamaMaxTokens);

        return ollamaService.generate(request);
    }

    private Uni<ForecastResult> buildAndPersistResult(ForecastRequest request, OllamaResponse ollamaResponse,
                                                     List<FlexibilityEventDTO> events,
                                                     Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations) {
        ForecastResult result = new ForecastResult();
        result.analysisType = request.analysisType;
        result.question = request.customQuestion != null ? request.customQuestion : "General analysis";
        ParsedOllamaResponse parsed = parseOllamaResponse(ollamaResponse.response);
        result.aiResponse = parsed.summary;
        result.sentiment = parsed.sentiment;
        result.confidenceScore = parsed.confidenceScore;
        result.eventsAnalyzed = events.size();
        result.correlatedOutcomes = correlationService.countCorrelatedOutcomes(correlations);
        result.analysisTimestamp = LocalDateTime.now();
        result.insights = parsed.insights;

        if (request.recommendedAction != null && "SUCCESS_RATE".equals(request.analysisType)) {
            result.successRate = correlationService.calculateSuccessRate(correlations, request.recommendedAction);
        }

        FlexibilityForecast forecast = new FlexibilityForecast();
        forecast.analysisType = result.analysisType;
        forecast.question = result.question;
        forecast.aiResponse = result.aiResponse;
        forecast.sentiment = result.sentiment;
        forecast.confidenceScore = result.confidenceScore;
        forecast.eventsAnalyzed = result.eventsAnalyzed;
        forecast.correlatedOutcomes = result.correlatedOutcomes;
        forecast.successRate = result.successRate;
        forecast.analysisTimestamp = result.analysisTimestamp;
        forecast.insightsJson = serializeInsights(result.insights);

        return forecast.save(client)
                .onItem().transform(id -> {
                    result.id = id;
                    return result;
                });
    }

    private ParsedOllamaResponse parseOllamaResponse(String response) {
        ParsedOllamaResponse parsed = new ParsedOllamaResponse();
        parsed.summary = response;
        parsed.sentiment = "UNKNOWN";
        parsed.confidenceScore = 0.0;
        parsed.insights = new HashMap<>();

        if (response == null || response.isBlank()) {
            return parsed;
        }

        try {
            JsonNode root = objectMapper.readTree(response);
            if (!root.isObject()) {
                return parsed;
            }

            String summary = getText(root, "summary");
            if (summary != null && !summary.isBlank()) {
                parsed.summary = summary;
            }

            String sentiment = getText(root, "sentiment");
            if (sentiment != null && !sentiment.isBlank()) {
                parsed.sentiment = sentiment;
            }

            Double confidence = getDouble(root, "confidence_score");
            if (confidence != null) {
                parsed.confidenceScore = confidence;
            }

            putIfPresent(parsed.insights, "key_patterns", root.get("key_patterns"));
            putIfPresent(parsed.insights, "risks", root.get("risks"));
            putIfPresent(parsed.insights, "recommendations", root.get("recommendations"));
            putIfPresent(parsed.insights, "data_quality_notes", root.get("data_quality_notes"));
            putIfPresent(parsed.insights, "statistics", root.get("statistics"));
        } catch (Exception e) {
            parsed.insights.put("parse_error", e.getMessage());
        }

        return parsed;
    }

    private String getText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText();
    }

    private Double getDouble(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asDouble();
        }
        if (node.isTextual()) {
            try {
                return Double.parseDouble(node.asText());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private void putIfPresent(Map<String, Object> insights, String key, JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        insights.put(key, objectMapper.convertValue(node, Object.class));
    }


    private String serializeInsights(Map<String, Object> insights) {
        try {
            return objectMapper.writeValueAsString(insights);
        } catch (Exception e) {
            return "{}";
        }
    }

    private ForecastResult createEmptyResult(ForecastRequest request) {
        ForecastResult result = new ForecastResult();
        result.analysisType = request.analysisType;
        result.question = request.customQuestion != null ? request.customQuestion : "General analysis";
        result.aiResponse = "No flexibility events found for analysis";
        result.sentiment = "NEUTRAL";
        result.confidenceScore = 0.0;
        result.eventsAnalyzed = 0;
        result.correlatedOutcomes = 0;
        result.analysisTimestamp = LocalDateTime.now();
        result.insights = new HashMap<>();
        result.insights.put("status", "no_data");
        return result;
    }

    private ForecastResult createErrorResult(ForecastRequest request, List<FlexibilityEventDTO> events,
                                           Map<FlexibilityEventDTO, List<BalancingRecommendationDTO>> correlations,
                                           String errorMessage) {
        ForecastResult result = new ForecastResult();
        result.analysisType = request.analysisType;
        result.question = request.customQuestion != null ? request.customQuestion : "General analysis";
        result.aiResponse = "Error during analysis: " + errorMessage;
        result.sentiment = "NEUTRAL";
        result.confidenceScore = 0.0;
        result.eventsAnalyzed = events.size();
        result.correlatedOutcomes = correlations != null ? correlationService.countCorrelatedOutcomes(correlations) : 0;
        result.analysisTimestamp = LocalDateTime.now();
        result.insights = new HashMap<>();
        result.insights.put("error", errorMessage);
        return result;
    }

    private static class ParsedOllamaResponse {
        private String summary;
        private String sentiment;
        private Double confidenceScore;
        private Map<String, Object> insights;
    }

}
