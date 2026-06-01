package org.acme;

import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.acme.dto.BatteryAssetDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.dto.BatchEvaluateRequest;
import org.acme.dto.BatchEvaluateResponse;
import org.acme.dto.ForecastResultRequest;
import org.acme.dto.ForecastResultResponse;
import org.acme.dto.OllamaPromptDTO;
import org.acme.entities.FlexibilityForecastResult;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("FlexibilityEvent")
public class FlexibilityEventResource {

    @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    @Inject
    @ConfigProperty(name = "myapp.schema.create", defaultValue = "true")
    boolean schemaCreate;

    @Inject
    @Channel("flexibility-offers")
    Emitter<String> flexibilityEmitter;

    @Inject
    @ConfigProperty(name = "kafka.bootstrap.servers")
    String kafka_servers;

    void config(@Observes StartupEvent ev) {
        if (schemaCreate) {
            initdb();
        }
    }

    private void initdb() {
        client.query("DROP TABLE IF EXISTS FlexibilityForecastResult").execute()
        .flatMap(r -> client.query("DROP TABLE IF EXISTS FlexibilityEvent").execute())
        .flatMap(r -> client.query("CREATE TABLE FlexibilityForecastResult (" +
                "id SERIAL PRIMARY KEY, " +
                "windowStart VARCHAR(50) NOT NULL, " +
                "windowEnd VARCHAR(50) NOT NULL, " +
                "flexibilityEventsCount INT NOT NULL, " +
                "gridBalancingCount INT NOT NULL, " +
                "forecastResult TEXT NOT NULL, " +
                "createdAt DATETIME NOT NULL)").execute())
        .flatMap(r -> client.query("CREATE TABLE FlexibilityEvent (" +
                "id SERIAL PRIMARY KEY, " +
                "assetId BIGINT UNSIGNED NOT NULL, " +
                "prosumerId BIGINT UNSIGNED NOT NULL, " +
                "eventType VARCHAR(100) NOT NULL, " +
                "soc_percent FLOAT, " +
                "recommendedAction VARCHAR(50), " +
                "marketPrice FLOAT, " +
                "incentiveAmount FLOAT, " +
                "gridCellId VARCHAR(100), " +
                "timestamp DATETIME NOT NULL)").execute())
        .await().indefinitely();
    }

    @GET
    public Multi<FlexibilityEvent> get() {
        return FlexibilityEvent.findAll(client);
    }

    @GET
    @Path("{id}")
    public Uni<Response> getSingle(Long id) {
        return FlexibilityEvent.findById(client, id)
                .onItem().transform(event -> event != null ? Response.ok(event) : Response.status(Response.Status.NOT_FOUND))
                .onItem().transform(ResponseBuilder::build);
    }

    @GET
    @Path("asset/{assetId}")
    public Multi<FlexibilityEvent> getByAsset(Long assetId) {
        return FlexibilityEvent.findByAssetId(client, assetId);
    }

    @GET
    @Path("type/{eventType}")
    public Multi<FlexibilityEvent> getByType(String eventType) {
        return FlexibilityEvent.findByEventType(client, eventType);
    }

    @POST
    @Path("evaluate")
    public Response evaluateBatch(BatchEvaluateRequest request) {
        Map<Long, Long> assetToProsumerMap = new HashMap<>();
        if (request.batteryAssets != null) {
            for (BatteryAssetDTO asset : request.batteryAssets) {
                assetToProsumerMap.put(asset.assetId, asset.prosumerId);
            }
        }

        List<FlexibilityEvent> suggestions = new ArrayList<>();
        if (request.telemetryList != null) {
            for (TelemetryDTO telemetry : request.telemetryList) {
                Long prosumerId = assetToProsumerMap.get(telemetry.asset_id);
                if (prosumerId == null) continue;

                FlexibilityEvent event = null;
                if (telemetry.State_of_Charge != null && telemetry.State_of_Charge > 90.0f) {
                    event = new FlexibilityEvent();
                    event.assetId = telemetry.asset_id;
                    event.prosumerId = prosumerId;
                    event.eventType = "ARBITRAGE_SELL";
                    event.soc_percent = telemetry.State_of_Charge;
                    event.recommendedAction = "DISCHARGE";
                    event.marketPrice = getCurrentMarketPrice();
                    event.incentiveAmount = calculateIncentive(telemetry.State_of_Charge);
                    event.gridCellId = telemetry.grid_cell_id;
                    event.timestamp = LocalDateTime.now();
                } else if (telemetry.State_of_Charge != null && telemetry.State_of_Charge < 20.0f) {
                    event = new FlexibilityEvent();
                    event.assetId = telemetry.asset_id;
                    event.prosumerId = prosumerId;
                    event.eventType = "BALANCING_UNAVAILABLE";
                    event.soc_percent = telemetry.State_of_Charge;
                    event.recommendedAction = "UNAVAILABLE";
                    event.gridCellId = telemetry.grid_cell_id;
                    event.timestamp = LocalDateTime.now();
                }

                if (event != null) {
                    suggestions.add(event);
                }
            }
        }

        return Response.ok(new BatchEvaluateResponse(suggestions)).build();
    }

    @GET
    @Path("logs")
    public Multi<FlexibilityEvent> getLogs(@QueryParam("from") String from, @QueryParam("to") String to) {
        return FlexibilityEvent.findByTimeWindow(client, LocalDateTime.parse(from), LocalDateTime.parse(to));
    }

    @GET
    @Path("prompt")
    public Uni<OllamaPromptDTO> getPrompt() {
        return FlexibilityEvent.findAll(client)
            .collect().asList()
            .onItem().transform(events -> {
                StringBuilder sb = new StringBuilder();
                sb.append("Analyze the following past VPPaaS flexibility events and determine: ");
                sb.append("1) the overall sentiment (Positive, Negative, or Neutral), ");
                sb.append("2) the success rate of DISCHARGE commands (i.e., how often they resulted in a stable grid state). ");
                sb.append("Events:\n");
                for (FlexibilityEvent e : events) {
                    sb.append(String.format("- [%s] Asset %d: %s -> %s (SoC: %.1f%%)\n",
                        e.timestamp, e.assetId, e.eventType, e.recommendedAction, e.soc_percent));
                }
                return new OllamaPromptDTO(sb.toString());
            });
    }

    @POST
    @Path("forecast")
    public Uni<Response> persistForecast(ForecastResultRequest request) {
        FlexibilityForecastResult result = new FlexibilityForecastResult();
        result.windowStart = request.windowStart;
        result.windowEnd = request.windowEnd;
        result.flexibilityEventsCount = request.flexibilityEventsCount;
        result.gridBalancingCount = request.gridBalancingCount;
        result.forecastResult = request.forecastResult;
        result.createdAt = LocalDateTime.now();
        return result.save(client)
            .onItem().transform(id -> Response.ok(new ForecastResultResponse(id)).build());
    }

    @POST
    @Path("emit")
    public Uni<Response> emitFlexibilityOffers(BatchEvaluateResponse request) {
        if (request.eventCreated == null || request.eventCreated.isEmpty()) {
            return Uni.createFrom().item(Response.ok(new BatchEvaluateResponse(new ArrayList<>())).build());
        }

        List<FlexibilityEvent> events = request.eventCreated;
        for (FlexibilityEvent event : events) {
            if (event.timestamp == null) {
                event.timestamp = LocalDateTime.now();
            }
        }

        List<Uni<Long>> saveUnis = events.stream()
            .map(event -> event.save(client))
            .collect(Collectors.toList());

        return Uni.combine().all().unis(saveUnis).combinedWith(ids -> {
            for (int i = 0; i < events.size(); i++) {
                events.get(i).id = (Long) ids.get(i);
            }
            for (FlexibilityEvent event : events) {
                if (!"ARBITRAGE_SELL".equals(event.eventType)) continue;
                String kafkaMessage = String.format(
                    "{\"eventId\":%d,\"assetId\":%d,\"prosumerId\":%d,\"eventType\":\"%s\",\"recommendedAction\":\"%s\",\"timestamp\":\"%s\"}",
                    event.id, event.assetId, event.prosumerId, event.eventType, event.recommendedAction, event.timestamp
                );
                flexibilityEmitter.send(kafkaMessage);
            }
            return Response.ok(new BatchEvaluateResponse(events)).build();
        });
    }

    private Float getCurrentMarketPrice() { // In production this SHOULD NOT be hardcoded, but for demo purposes we return a fixed value
        return 150.0f;
    }

    private Float calculateIncentive(Float soc) {
        return (soc - 90.0f) * 2.0f;
    }
}
