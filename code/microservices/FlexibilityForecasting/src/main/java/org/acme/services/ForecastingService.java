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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

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

    @ConfigProperty(name = "ollama.temperature", defaultValue = "0.7")
    double ollamaTemperature;

    @ConfigProperty(name = "ollama.max-tokens", defaultValue = "1000")
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
        result.aiResponse = ollamaResponse.response;
        result.eventsAnalyzed = events.size();
        result.correlatedOutcomes = correlationService.countCorrelatedOutcomes(correlations);
        result.analysisTimestamp = LocalDateTime.now();

        parseOllamaResponse(ollamaResponse.response, result, request);

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

    private void parseOllamaResponse(String response, ForecastResult result, ForecastRequest request) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);

            if (jsonNode.has("sentiment")) {
                result.sentiment = jsonNode.get("sentiment").asText();
            }

            if (jsonNode.has("confidence")) {
                result.confidenceScore = jsonNode.get("confidence").asDouble();
            }

            if (jsonNode.has("success")) {
                result.sentiment = jsonNode.get("success").asBoolean() ? "POSITIVE" : "NEGATIVE";
            }

            if (jsonNode.has("success_rate")) {
                result.successRate = jsonNode.get("success_rate").asDouble();
            }

            if (jsonNode.has("insights")) {
                Map<String, Object> insights = new HashMap<>();
                jsonNode.get("insights").forEach(insight -> {
                    insights.put("insight_" + insights.size(), insight.asText());
                });
                result.insights = insights;
            }

            if (jsonNode.has("correlation_strength")) {
                if (result.insights == null) {
                    result.insights = new HashMap<>();
                }
                result.insights.put("correlation_strength", jsonNode.get("correlation_strength").asText());
            }

            if (jsonNode.has("summary")) {
                if (result.insights == null) {
                    result.insights = new HashMap<>();
                }
                result.insights.put("summary", jsonNode.get("summary").asText());
            }

        } catch (Exception e) {
            result.sentiment = "NEUTRAL";
            result.confidenceScore = 50.0;
            result.insights = new HashMap<>();
            result.insights.put("raw_response", response);
            result.insights.put("parse_error", e.getMessage());
        }
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

    public Uni<String> checkOllamaHealth() {
        return ollamaService.listModels()
                .onItem().transform(response -> "Ollama is available")
                .onFailure().recoverWithItem(throwable -> "Ollama is unavailable: " + throwable.getMessage());
    }
}
