package org.acme;

import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;

import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Query;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import io.vertx.sqlclient.RowIterator;
import jakarta.ws.rs.core.Response;
import org.acme.dto.GridBalancingEvaluateRequest;
import org.acme.dto.GridBalancingEvaluateResponse;
import org.acme.dto.GridCellDTO;
import org.acme.dto.TelemetryDTO;
import org.acme.entities.BalancingRecommendation;
import org.acme.services.GridBalancingRecommendationService;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;

class GridBalancingRecommendationResourceTest {

    GridBalancingRecommendationResource resource;
    private MySQLPool client;
    private GridBalancingRecommendationService recommendationService;

    @BeforeEach
    void setup() {
        resource = new GridBalancingRecommendationResource();
        client = Mockito.mock(MySQLPool.class);
        recommendationService = Mockito.mock(GridBalancingRecommendationService.class);
        injectClient(resource, client);
        injectSchemaCreate(resource, false);
        injectService(resource, recommendationService);
        injectThresholdPercent(resource, 0.9);
    }

    @Test
    void getAll_returnsList() {
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 1, 15, 11, 30);
        Row row1 = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp1);
        Row row2 = balancingRecommendationRow(2L, "GRID_C", null, 100.0, null, 10.0, null, 0.9, "NO_TARGET", "No available target", timestamp2);
        stubQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        List<BalancingRecommendation> result = resource.getAll().collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).sourceGridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(0).targetGridCellId, is("GRID_B"));
        MatcherAssert.assertThat(result.get(0).sourceNetLoadKw, is(95.0));
        MatcherAssert.assertThat(result.get(0).targetHeadroomKw, is(40.0));
        MatcherAssert.assertThat(result.get(0).overloadKw, is(5.0));
        MatcherAssert.assertThat(result.get(0).transferableKw, is(5.0));
        MatcherAssert.assertThat(result.get(0).thresholdPercent, is(0.9));
        MatcherAssert.assertThat(result.get(0).status, is("RECOMMENDED"));
        MatcherAssert.assertThat(result.get(0).rationale, is("Transfer 5kW to GRID_B"));
        MatcherAssert.assertThat(result.get(0).createdAt, is(timestamp1));
    }

    @Test
    void getById_returnsEntity() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp);
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE id = ?", rowSetWithRows(row));

        Response response = resource.getById(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        BalancingRecommendation result = (BalancingRecommendation) response.getEntity();
        MatcherAssert.assertThat(result.id, is(1L));
        MatcherAssert.assertThat(result.sourceGridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.targetGridCellId, is("GRID_B"));
        MatcherAssert.assertThat(result.status, is("RECOMMENDED"));
    }

    @Test
    void getById_returnsNotFound() {
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE id = ?", rowSetWithRows());

        Response response = resource.getById(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getBySource_returnsFiltered() {
        LocalDateTime timestamp = LocalDateTime.of(2024, 1, 15, 10, 30);
        Row row1 = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp);
        Row row2 = balancingRecommendationRow(2L, "GRID_A", "GRID_C", 98.0, 30.0, 8.0, 8.0, 0.9, "RECOMMENDED", "Transfer 8kW to GRID_C", timestamp);
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE sourceGridCellId = ? ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        List<BalancingRecommendation> result = resource.getBySource("GRID_A").collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).sourceGridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(1).sourceGridCellId, is("GRID_A"));
    }

    @Test
    void evaluate_withOverloadAndTarget_createsRECOMMENDED() {
        GridCellDTO sourceCell = createGridCell("GRID_A", 100.0);
        List<GridCellDTO> neighbourCells = Collections.singletonList(createGridCell("GRID_B", 100.0));
        List<TelemetryDTO> telemetry = Arrays.asList(
            createEVChargerTelemetry(1L, "GRID_A", 95.0f),
            createEVChargerTelemetry(2L, "GRID_B", 50.0f)
        );

        BalancingRecommendation expectedRec = new BalancingRecommendation();
        expectedRec.sourceGridCellId = "GRID_A";
        expectedRec.targetGridCellId = "GRID_B";
        expectedRec.status = "RECOMMENDED";
        expectedRec.overloadKw = 5.0;
        expectedRec.transferableKw = 5.0;
        expectedRec.sourceNetLoadKw = 95.0;
        expectedRec.targetHeadroomKw = 40.0;
        expectedRec.thresholdPercent = 0.9;
        expectedRec.rationale = "Transfer 5kW to GRID_B";
        expectedRec.createdAt = LocalDateTime.now();

        Mockito.when(recommendationService.evaluateRecommendations(Mockito.any(GridBalancingEvaluateRequest.class)))
               .thenReturn(Arrays.asList(expectedRec));

        GridBalancingEvaluateRequest request = new GridBalancingEvaluateRequest();
        request.sourceCell = sourceCell;
        request.neighbourCells = neighbourCells;
        request.allTelemetry = telemetry;

        Response response = resource.evaluate(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridBalancingEvaluateResponse result = (GridBalancingEvaluateResponse) response.getEntity();
        MatcherAssert.assertThat(result.eventCreated, hasSize(1));
        MatcherAssert.assertThat(result.hasGridBalancing, is(true));
        MatcherAssert.assertThat(result.eventCreated.get(0).status, is("RECOMMENDED"));
        MatcherAssert.assertThat(result.eventCreated.get(0).targetGridCellId, is("GRID_B"));
    }

    @Test
    void evaluate_withOverloadNoTarget_createsNO_TARGET() {
        GridCellDTO sourceCell = createGridCell("GRID_A", 100.0);
        List<GridCellDTO> neighbourCells = Collections.singletonList(createGridCell("GRID_B", 100.0));
        List<TelemetryDTO> telemetry = Arrays.asList(
            createEVChargerTelemetry(1L, "GRID_A", 95.0f),
            createEVChargerTelemetry(2L, "GRID_B", 95.0f)
        );

        BalancingRecommendation expectedRec = new BalancingRecommendation();
        expectedRec.sourceGridCellId = "GRID_A";
        expectedRec.targetGridCellId = null;
        expectedRec.status = "NO_TARGET";
        expectedRec.overloadKw = 5.0;
        expectedRec.transferableKw = null;
        expectedRec.sourceNetLoadKw = 95.0;
        expectedRec.targetHeadroomKw = null;
        expectedRec.thresholdPercent = 0.9;
        expectedRec.rationale = "No available target grid cell";
        expectedRec.createdAt = LocalDateTime.now();

        Mockito.when(recommendationService.evaluateRecommendations(Mockito.any(GridBalancingEvaluateRequest.class)))
               .thenReturn(Arrays.asList(expectedRec));

        GridBalancingEvaluateRequest request = new GridBalancingEvaluateRequest();
        request.sourceCell = sourceCell;
        request.neighbourCells = neighbourCells;
        request.allTelemetry = telemetry;

        Response response = resource.evaluate(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridBalancingEvaluateResponse result = (GridBalancingEvaluateResponse) response.getEntity();
        MatcherAssert.assertThat(result.eventCreated, hasSize(1));
        MatcherAssert.assertThat(result.hasGridBalancing, is(true));
        MatcherAssert.assertThat(result.eventCreated.get(0).status, is("NO_TARGET"));
        MatcherAssert.assertThat(result.eventCreated.get(0).targetGridCellId, is((String) null));
    }

    @Test
    void evaluate_withNoOverload_returnsEmptyList() {
        GridCellDTO sourceCell = createGridCell("GRID_A", 100.0);
        List<GridCellDTO> neighbourCells = Collections.singletonList(createGridCell("GRID_B", 100.0));
        List<TelemetryDTO> telemetry = Arrays.asList(
            createEVChargerTelemetry(1L, "GRID_A", 50.0f),
            createEVChargerTelemetry(2L, "GRID_B", 60.0f)
        );

        Mockito.when(recommendationService.evaluateRecommendations(Mockito.any(GridBalancingEvaluateRequest.class)))
               .thenReturn(Collections.emptyList());

        GridBalancingEvaluateRequest request = new GridBalancingEvaluateRequest();
        request.sourceCell = sourceCell;
        request.neighbourCells = neighbourCells;
        request.allTelemetry = telemetry;

        Response response = resource.evaluate(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridBalancingEvaluateResponse result = (GridBalancingEvaluateResponse) response.getEntity();
        MatcherAssert.assertThat(result.eventCreated, hasSize(0));
        MatcherAssert.assertThat(result.hasGridBalancing, is(false));
    }

    @Test
    void evaluate_withOverloadAndMultipleNeighbours_createsRECOMMENDED() {
        GridCellDTO sourceCell = createGridCell("GRID_A", 100.0);
        List<GridCellDTO> neighbourCells = Arrays.asList(
            createGridCell("GRID_B", 100.0),
            createGridCell("GRID_C", 100.0)
        );
        List<TelemetryDTO> telemetry = Arrays.asList(
            createEVChargerTelemetry(1L, "GRID_A", 95.0f),
            createEVChargerTelemetry(2L, "GRID_B", 92.0f),
            createEVChargerTelemetry(3L, "GRID_C", 50.0f)
        );

        BalancingRecommendation rec = new BalancingRecommendation();
        rec.sourceGridCellId = "GRID_A";
        rec.targetGridCellId = "GRID_C";
        rec.status = "RECOMMENDED";
        rec.overloadKw = 5.0;

        Mockito.when(recommendationService.evaluateRecommendations(Mockito.any(GridBalancingEvaluateRequest.class)))
               .thenReturn(Collections.singletonList(rec));

        GridBalancingEvaluateRequest request = new GridBalancingEvaluateRequest();
        request.sourceCell = sourceCell;
        request.neighbourCells = neighbourCells;
        request.allTelemetry = telemetry;

        Response response = resource.evaluate(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridBalancingEvaluateResponse result = (GridBalancingEvaluateResponse) response.getEntity();
        MatcherAssert.assertThat(result.eventCreated, hasSize(1));
        MatcherAssert.assertThat(result.hasGridBalancing, is(true));
        MatcherAssert.assertThat(result.eventCreated.get(0).targetGridCellId, is("GRID_C"));
    }

    @Test
    void evaluate_withEmptyData_returnsEmptyList() {
        Mockito.when(recommendationService.evaluateRecommendations(Mockito.any(GridBalancingEvaluateRequest.class)))
               .thenReturn(Collections.emptyList());

        GridBalancingEvaluateRequest request = new GridBalancingEvaluateRequest();
        request.sourceCell = null;
        request.neighbourCells = Collections.emptyList();
        request.allTelemetry = Collections.emptyList();

        Response response = resource.evaluate(request);
        MatcherAssert.assertThat(response.getStatus(), is(200));
        GridBalancingEvaluateResponse result = (GridBalancingEvaluateResponse) response.getEntity();
        MatcherAssert.assertThat(result.eventCreated, hasSize(0));
        MatcherAssert.assertThat(result.hasGridBalancing, is(false));
    }

    @Test
    void emit_savesAndReturns200() {
        BalancingRecommendation rec = new BalancingRecommendation();
        rec.sourceGridCellId = "GRID_A";
        rec.targetGridCellId = "GRID_B";
        rec.sourceNetLoadKw = 95.0;
        rec.targetHeadroomKw = 40.0;
        rec.overloadKw = 5.0;
        rec.transferableKw = 5.0;
        rec.thresholdPercent = 0.9;
        rec.status = "RECOMMENDED";
        rec.rationale = "Transfer 5kW to GRID_B";
        rec.createdAt = LocalDateTime.now();

        BalancingRecommendation saved = new BalancingRecommendation();
        saved.id = 1L;
        saved.sourceGridCellId = "GRID_A";
        saved.status = "RECOMMENDED";

        List<BalancingRecommendation> savedList = Collections.singletonList(saved);
        Mockito.when(recommendationService.emitAll(Mockito.anyList()))
               .thenReturn(Uni.createFrom().item(savedList));

        Response response = resource.emit(Collections.singletonList(rec)).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(200));
        List<BalancingRecommendation> result = (List<BalancingRecommendation>) response.getEntity();
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).sourceGridCellId, is("GRID_A"));
    }

    @Test
    void emit_appliesDefaults() {
        BalancingRecommendation rec = new BalancingRecommendation();
        rec.sourceGridCellId = "GRID_A";
        rec.sourceNetLoadKw = 95.0;
        rec.overloadKw = 5.0;

        Mockito.when(recommendationService.emitAll(Mockito.anyList()))
               .thenAnswer(inv -> Uni.createFrom().item(() -> inv.<List<BalancingRecommendation>>getArgument(0)));

        resource.emit(Collections.singletonList(rec)).await().indefinitely();
        MatcherAssert.assertThat(rec.status, is("MANUAL"));
        MatcherAssert.assertThat(rec.createdAt, notNullValue());
        MatcherAssert.assertThat(rec.thresholdPercent, is(0.9));
    }

    @Test
    void create_withValidData_returns201() {
        BalancingRecommendation recommendation = new BalancingRecommendation();
        recommendation.sourceGridCellId = "GRID_A";
        recommendation.targetGridCellId = "GRID_B";
        recommendation.sourceNetLoadKw = 95.0;
        recommendation.targetHeadroomKw = 40.0;
        recommendation.overloadKw = 5.0;
        recommendation.transferableKw = 5.0;
        recommendation.thresholdPercent = 0.9;
        recommendation.status = "RECOMMENDED";
        recommendation.rationale = "Transfer 5kW to GRID_B";
        recommendation.createdAt = LocalDateTime.now();

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(123L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)", insertResult);

        Response response = resource.create(recommendation).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(response.getLocation().toString(), containsString("/123"));
    }

    @Test
    void create_appliesDefaults() {
        BalancingRecommendation recommendation = new BalancingRecommendation();
        recommendation.sourceGridCellId = "GRID_A";
        recommendation.targetGridCellId = "GRID_B";
        recommendation.sourceNetLoadKw = 95.0;
        recommendation.targetHeadroomKw = 40.0;
        recommendation.overloadKw = 5.0;
        recommendation.transferableKw = 5.0;
        recommendation.rationale = "Transfer 5kW to GRID_B";

        RowSet<Row> insertResult = rowSetWithRowCount(1);
        Mockito.when(insertResult.property(io.vertx.mutiny.mysqlclient.MySQLClient.LAST_INSERTED_ID))
               .thenReturn(123L);
        stubPreparedQuery("INSERT INTO BalancingRecommendation(sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt) VALUES (?,?,?,?,?,?,?,?,?,?)", insertResult);

        Response response = resource.create(recommendation).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(201));
        MatcherAssert.assertThat(recommendation.createdAt, notNullValue());
        MatcherAssert.assertThat(recommendation.status, is("MANUAL"));
        MatcherAssert.assertThat(recommendation.thresholdPercent, is(0.9));
    }

    @Test
    void update_withExistingId_returns204() {
        BalancingRecommendation recommendation = new BalancingRecommendation();
        recommendation.sourceGridCellId = "GRID_A";
        recommendation.targetGridCellId = "GRID_B";
        recommendation.sourceNetLoadKw = 95.0;
        recommendation.targetHeadroomKw = 40.0;
        recommendation.overloadKw = 5.0;
        recommendation.transferableKw = 5.0;
        recommendation.thresholdPercent = 0.9;
        recommendation.status = "RECOMMENDED";
        recommendation.rationale = "Transfer 5kW to GRID_B";
        recommendation.createdAt = LocalDateTime.now();

        RowSet<Row> updateResult = rowSetWithRowCount(1);
        stubPreparedQuery("UPDATE BalancingRecommendation SET sourceGridCellId = ?, targetGridCellId = ?, sourceNetLoadKw = ?, targetHeadroomKw = ?, overloadKw = ?, transferableKw = ?, thresholdPercent = ?, status = ?, rationale = ?, createdAt = ? WHERE id = ?", updateResult);

        Response response = resource.update(1L, recommendation).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void update_withNonExistingId_returns404() {
        BalancingRecommendation recommendation = new BalancingRecommendation();
        recommendation.sourceGridCellId = "GRID_A";
        recommendation.targetGridCellId = "GRID_B";
        recommendation.sourceNetLoadKw = 95.0;
        recommendation.targetHeadroomKw = 40.0;
        recommendation.overloadKw = 5.0;
        recommendation.transferableKw = 5.0;
        recommendation.thresholdPercent = 0.9;
        recommendation.status = "RECOMMENDED";
        recommendation.rationale = "Transfer 5kW to GRID_B";
        recommendation.createdAt = LocalDateTime.now();

        RowSet<Row> updateResult = rowSetWithRowCount(0);
        stubPreparedQuery("UPDATE BalancingRecommendation SET sourceGridCellId = ?, targetGridCellId = ?, sourceNetLoadKw = ?, targetHeadroomKw = ?, overloadKw = ?, transferableKw = ?, thresholdPercent = ?, status = ?, rationale = ?, createdAt = ? WHERE id = ?", updateResult);

        Response response = resource.update(99L, recommendation).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void delete_withExistingId_returns204() {
        RowSet<Row> deleteResult = rowSetWithRowCount(1);
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", deleteResult);

        Response response = resource.delete(1L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(204));
    }

    @Test
    void delete_withNonExistingId_returns404() {
        RowSet<Row> deleteResult = rowSetWithRowCount(0);
        stubPreparedQuery("DELETE FROM BalancingRecommendation WHERE id = ?", deleteResult);

        Response response = resource.delete(99L).await().indefinitely();
        MatcherAssert.assertThat(response.getStatus(), is(404));
    }

    @Test
    void getByTimeWindow_returnsList() {
        LocalDateTime from = LocalDateTime.of(2024, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2024, 12, 31, 23, 59);
        LocalDateTime timestamp1 = LocalDateTime.of(2024, 1, 15, 10, 30);
        LocalDateTime timestamp2 = LocalDateTime.of(2024, 6, 20, 14, 0);
        Row row1 = balancingRecommendationRow(1L, "GRID_A", "GRID_B", 95.0, 40.0, 5.0, 5.0, 0.9, "RECOMMENDED", "Transfer 5kW to GRID_B", timestamp1);
        Row row2 = balancingRecommendationRow(2L, "GRID_C", null, 100.0, null, 10.0, null, 0.9, "NO_TARGET", "No available target", timestamp2);
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE createdAt >= ? AND createdAt <= ? ORDER BY createdAt DESC", rowSetWithRows(row1, row2));

        List<BalancingRecommendation> result = resource.getByTimeWindow(from.toString(), to.toString()).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(2));
        MatcherAssert.assertThat(result.get(0).id, is(1L));
        MatcherAssert.assertThat(result.get(0).sourceGridCellId, is("GRID_A"));
        MatcherAssert.assertThat(result.get(1).id, is(2L));
        MatcherAssert.assertThat(result.get(1).status, is("NO_TARGET"));
    }

    @Test
    void getByTimeWindow_returnsEmptyList() {
        LocalDateTime from = LocalDateTime.of(2020, 1, 1, 0, 0);
        LocalDateTime to = LocalDateTime.of(2020, 12, 31, 23, 59);
        stubPreparedQuery("SELECT id, sourceGridCellId, targetGridCellId, sourceNetLoadKw, targetHeadroomKw, overloadKw, transferableKw, thresholdPercent, status, rationale, createdAt FROM BalancingRecommendation WHERE createdAt >= ? AND createdAt <= ? ORDER BY createdAt DESC", rowSetWithRows());

        List<BalancingRecommendation> result = resource.getByTimeWindow(from.toString(), to.toString()).collect().asList().await().indefinitely();
        MatcherAssert.assertThat(result, hasSize(0));
    }

    private void injectClient(GridBalancingRecommendationResource target, MySQLPool pool) {
        try {
            Field field = GridBalancingRecommendationResource.class.getDeclaredField("client");
            field.setAccessible(true);
            field.set(target, pool);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject MySQLPool", e);
        }
    }

    private void injectSchemaCreate(GridBalancingRecommendationResource target, boolean value) {
        try {
            Field field = GridBalancingRecommendationResource.class.getDeclaredField("schemaCreate");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject schemaCreate", e);
        }
    }

    private void injectService(GridBalancingRecommendationResource target, GridBalancingRecommendationService service) {
        try {
            Field field = GridBalancingRecommendationResource.class.getDeclaredField("recommendationService");
            field.setAccessible(true);
            field.set(target, service);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject GridBalancingRecommendationService", e);
        }
    }

    private void injectThresholdPercent(GridBalancingRecommendationResource target, double value) {
        try {
            Field field = GridBalancingRecommendationResource.class.getDeclaredField("thresholdPercent");
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException("Failed to inject thresholdPercent", e);
        }
    }

    private void stubQuery(String sql, RowSet<Row> rowSet) {
        Query<RowSet<Row>> query = Mockito.mock(Query.class);
        Mockito.when(query.execute()).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.query(sql)).thenReturn(query);
    }

    private void stubPreparedQuery(String sql, RowSet<Row> rowSet) {
        PreparedQuery<RowSet<Row>> preparedQuery = Mockito.mock(PreparedQuery.class);
        Mockito.when(preparedQuery.execute(Mockito.any(Tuple.class))).thenReturn(Uni.createFrom().item(rowSet));
        Mockito.when(client.preparedQuery(sql)).thenReturn(preparedQuery);
    }

    private RowSet<Row> rowSetWithRows(Row... rows) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        io.vertx.mutiny.sqlclient.RowIterator<Row> iterator =
                io.vertx.mutiny.sqlclient.RowIterator.newInstance(new ListRowIterator(Arrays.asList(rows)));
        Mockito.when(rowSet.iterator()).thenReturn(iterator);
        return rowSet;
    }

    private RowSet<Row> rowSetWithRowCount(int count) {
        RowSet<Row> rowSet = Mockito.mock(RowSet.class);
        Mockito.when(rowSet.rowCount()).thenReturn(count);
        return rowSet;
    }

    private Row balancingRecommendationRow(Long id, String sourceGridCellId, String targetGridCellId,
                                           Double sourceNetLoadKw, Double targetHeadroomKw, Double overloadKw,
                                           Double transferableKw, Double thresholdPercent, String status,
                                           String rationale, LocalDateTime createdAt) {
        Row row = Mockito.mock(Row.class);
        Mockito.when(row.getLong("id")).thenReturn(id);
        Mockito.when(row.getString("sourceGridCellId")).thenReturn(sourceGridCellId);
        Mockito.when(row.getString("targetGridCellId")).thenReturn(targetGridCellId);
        Mockito.when(row.getDouble("sourceNetLoadKw")).thenReturn(sourceNetLoadKw);
        Mockito.when(row.getDouble("targetHeadroomKw")).thenReturn(targetHeadroomKw);
        Mockito.when(row.getDouble("overloadKw")).thenReturn(overloadKw);
        Mockito.when(row.getDouble("transferableKw")).thenReturn(transferableKw);
        Mockito.when(row.getDouble("thresholdPercent")).thenReturn(thresholdPercent);
        Mockito.when(row.getString("status")).thenReturn(status);
        Mockito.when(row.getString("rationale")).thenReturn(rationale);
        Mockito.when(row.getLocalDateTime("createdAt")).thenReturn(createdAt);
        return row;
    }

    private TelemetryDTO createEVChargerTelemetry(Long assetId, String gridCellId, Float chargingRate) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.asset_type = "EV_CHARGER";
        dto.grid_cell_id = gridCellId;
        dto.Charging_Rate = chargingRate;
        dto.timeStamp = LocalDateTime.now();
        return dto;
    }

    private TelemetryDTO createSolarTelemetry(Long assetId, String gridCellId, Float currentGeneration) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.asset_type = "SOLAR";
        dto.grid_cell_id = gridCellId;
        dto.Current_Generation = currentGeneration;
        dto.timeStamp = LocalDateTime.now();
        return dto;
    }

    private TelemetryDTO createBatteryTelemetry(Long assetId, String gridCellId, Float currentOutput) {
        TelemetryDTO dto = new TelemetryDTO();
        dto.asset_id = assetId;
        dto.asset_type = "BATTERY";
        dto.grid_cell_id = gridCellId;
        dto.Current_Output = currentOutput;
        dto.timeStamp = LocalDateTime.now();
        return dto;
    }

    private GridCellDTO createGridCell(String gridCellId, Double maxCapacity) {
        GridCellDTO dto = new GridCellDTO();
        dto.gridCellId = gridCellId;
        dto.maxCapacity = maxCapacity;
        dto.utilityOperatorId = 1L;
        dto.geographicBoundaries = "Test boundaries";
        return dto;
    }

    private static final class ListRowIterator implements RowIterator<Row> {
        private final java.util.Iterator<Row> iterator;

        private ListRowIterator(List<Row> rows) {
            this.iterator = rows.iterator();
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public Row next() {
            return iterator.next();
        }
    }
}
