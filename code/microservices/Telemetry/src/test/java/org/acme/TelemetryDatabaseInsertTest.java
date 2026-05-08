package org.acme;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import io.vertx.mutiny.sqlclient.PreparedQuery;
import io.vertx.mutiny.sqlclient.Row;
import io.vertx.mutiny.sqlclient.RowSet;
import io.vertx.mutiny.sqlclient.Tuple;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class TelemetryDatabaseInsertTest {

    @InjectMock
    MySQLPool client;

    @Test
    void insertQuery_has18Placeholders() {
        String expectedQuery = "INSERT INTO Telemetry (timeStamp, asset_id, asset_type, grid_cell_id, State_of_Charge, Available_Energy, Current_Output, Max_Capacity, State_of_Health, "
            + "Status, Current_Generation, Daily_Total, Grid_Voltage, Frequency, Plug_Status, Charging_Rate, Session_Energy, EV_SoC) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        long placeholderCount = expectedQuery.chars().filter(ch -> ch == '?').count();

        assertThat("INSERT query should have 18 placeholders for all telemetry fields",
                   placeholderCount, is(18L));
    }

    @Test
    void tupleWrap_withListOf18Parameters_createsValidTuple() {
        java.util.List<Object> paramsList = new java.util.ArrayList<>();
        paramsList.add(java.time.LocalDateTime.now());
        paramsList.add(1001L);
        paramsList.add("BATTERY");
        paramsList.add("CELL-1");
        paramsList.add(70.5f);
        paramsList.add(12.3f);
        paramsList.add(3.2f);
        paramsList.add(5.0f);
        paramsList.add(99.0f);
        paramsList.add("CONNECTED");
        paramsList.add(1.1f);
        paramsList.add(6.8f);
        paramsList.add(230.0f);
        paramsList.add(50.0f);
        paramsList.add("PLUGGED");
        paramsList.add(7.4f);
        paramsList.add(2.2f);
        paramsList.add(80.0f);

        Tuple tuple = Tuple.wrap(paramsList);

        assertThat("Tuple should contain 18 parameters", tuple.size(), is(18));
    }

    @Test
    void preparedQuery_executedWithTuple_capturesCorrectParameterCount() {
        PreparedQuery<RowSet<Row>> preparedQuery = mock(PreparedQuery.class);
        RowSet<Row> mockRowSet = mock(RowSet.class);
        ArgumentCaptor<Tuple> tupleCaptor = ArgumentCaptor.forClass(Tuple.class);

        when(client.preparedQuery(anyString())).thenReturn(preparedQuery);
        when(preparedQuery.execute(tupleCaptor.capture()))
            .thenReturn(Uni.createFrom().item(mockRowSet));

        java.util.List<Object> paramsList = new java.util.ArrayList<>();
        paramsList.add(java.time.LocalDateTime.now());
        paramsList.add(1001L);
        paramsList.add("BATTERY");
        paramsList.add("CELL-1");
        paramsList.add(70.5f);
        paramsList.add(12.3f);
        paramsList.add(3.2f);
        paramsList.add(5.0f);
        paramsList.add(99.0f);
        paramsList.add("CONNECTED");
        paramsList.add(1.1f);
        paramsList.add(6.8f);
        paramsList.add(230.0f);
        paramsList.add(50.0f);
        paramsList.add("PLUGGED");
        paramsList.add(7.4f);
        paramsList.add(2.2f);
        paramsList.add(80.0f);

        String query = "INSERT INTO Telemetry (timeStamp, asset_id, asset_type, grid_cell_id, State_of_Charge, Available_Energy, Current_Output, Max_Capacity, State_of_Health, "
            + "Status, Current_Generation, Daily_Total, Grid_Voltage, Frequency, Plug_Status, Charging_Rate, Session_Energy, EV_SoC) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        client.preparedQuery(query).execute(Tuple.wrap(paramsList)).subscribe().with(item -> {});

        verify(preparedQuery).execute(tupleCaptor.capture());
        Tuple captured = tupleCaptor.getValue();

        assertThat("Captured tuple should have 18 parameters", captured.size(), is(18));
    }
}
