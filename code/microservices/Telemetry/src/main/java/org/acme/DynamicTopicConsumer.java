package org.acme;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import org.json.*;

import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Message;
import io.smallrye.reactive.messaging.kafka.api.OutgoingKafkaRecordMetadata;


public class DynamicTopicConsumer extends Thread  {
    private String kafka_servers;
    private String topic;

     @Inject
    io.vertx.mutiny.mysqlclient.MySQLPool client;

    private Emitter<String> batteryEmitter;
    private Emitter<String> solarEmitter;
    private Emitter<String> chargerEmitter;

    public DynamicTopicConsumer(String topic_received, String kafka_servers_received,
                               io.vertx.mutiny.mysqlclient.MySQLPool client_received,
                               Emitter<String> batteryEmitter_received,
                               Emitter<String> solarEmitter_received,
                               Emitter<String> chargerEmitter_received)
    {
        topic = topic_received;
        kafka_servers = kafka_servers_received;
        client = client_received;
        batteryEmitter = batteryEmitter_received;
        solarEmitter = solarEmitter_received;
        chargerEmitter = chargerEmitter_received;
    }

    public void run() 
	{
	    try 
		{
            Properties properties = new Properties();
            properties.put("bootstrap.servers", kafka_servers);
            properties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
            properties.put("group.id", "your-group-id");
    
            try (Consumer<String, String> consumer = new KafkaConsumer<>(properties)) {
                consumer.subscribe(Collections.singletonList(topic));
    
                while (true) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    for (ConsumerRecord<String, String> record : records)
                    {

                        System.out.printf("topic = %s, partition = %s, offset = %d,key = %s, value = %s\n",
                        record.topic(), record.partition(), record.offset(),
                        record.key(), record.value());				

                        String jsonString = record.value() ; 
                        JSONObject obj = new JSONObject(jsonString);
                        String timeStamp = obj.getString("timeStamp");
                        String asset_type = obj.getString("asset_type");
                        String asset_id = obj.getString("asset_id");
                        String grid_cell_id = obj.getString("grid_cell_id");

                        Float soc_percent = null;
                        Float energy_available_kwh = null;
                        Float active_power_kw = null;
                        Float max_discharge_power_kw = null;
                        Float soh_percent = null;
                        String connection_status   = null;      
                        Float generation_kw = null;
                        Float daily_yield_kwh = null;
                        Float ac_voltage_v = null;
                        Float grid_frequency_hz = null;
                        String connector_status = null;
                        Float charging_power_kw = null;
                        Float session_energy_kwh = null;
                        Float ev_soc_percent = null;

                        if ( asset_type.compareTo("BATTERY") == 0 ) 
                        {
                            soc_percent = obj.getJSONObject("payload").getFloat("soc_percent");
                            energy_available_kwh = obj.getJSONObject("payload").getFloat("energy_available_kwh");
                            active_power_kw = obj.getJSONObject("payload").getFloat("active_power_kw");
                            max_discharge_power_kw = obj.getJSONObject("payload").getFloat("max_discharge_power_kw");
                            soh_percent = obj.getJSONObject("payload").getFloat("soh_percent");
                            connection_status = obj.getJSONObject("payload").getString("connection_status");
                        }
                        else if ( asset_type.compareTo("SOLAR")  == 0  ) 
                        {
                            generation_kw = obj.getJSONObject("payload").getFloat("generation_kw");
                            daily_yield_kwh = obj.getJSONObject("payload").getFloat("daily_yield_kwh");
                            ac_voltage_v = obj.getJSONObject("payload").getFloat("ac_voltage_v");
                            grid_frequency_hz = obj.getJSONObject("payload").getFloat("grid_frequency_hz");
                        }
                        else if( asset_type.compareTo("EV_CHARGER")  == 0  ) 
                        {
                            connector_status = obj.getJSONObject("payload").getString("connector_status");
                            charging_power_kw = obj.getJSONObject("payload").getFloat("charging_power_kw");
                            session_energy_kwh = obj.getJSONObject("payload").getFloat("session_energy_kwh");
                            ev_soc_percent = obj.getJSONObject("payload").getFloat("ev_soc_percent");
                        }
                        

                        String query = "INSERT INTO Telemetry (timeStamp, asset_id, asset_type, grid_cell_id, State_of_Charge, Available_Energy, Current_Output, Max_Capacity, State_of_Health, "
                        + "Status, Current_Generation, Daily_Total, Grid_Voltage, Frequency, Plug_Status, Charging_Rate, Session_Energy, EV_SoC) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                        client.preparedQuery(query).execute(io.vertx.mutiny.sqlclient.Tuple.of(
                            timeStamp,
                            asset_id,
                            asset_type,
                            grid_cell_id,
                            soc_percent,
                            energy_available_kwh,
                            active_power_kw,
                            max_discharge_power_kw,
                            soh_percent,
                            connection_status,
                            generation_kw,
                            daily_yield_kwh,
                            ac_voltage_v,
                            grid_frequency_hz,
                            connector_status,
                            charging_power_kw,
                            session_energy_kwh,
                            ev_soc_percent
                        )).await().indefinitely();

                        publishTelemetryEvent(jsonString, asset_id, asset_type);
                    }
                }
            }    
        }
        catch (Exception e)
		{  System.out.println("Exception is caught:" + e);  }
    }

    private void publishTelemetryEvent(String telemetryData, String assetId, String assetType) {
        Message<String> message = Message.of(telemetryData)
            .addMetadata(OutgoingKafkaRecordMetadata.builder().withKey(assetId).build());

        switch (assetType) {
            case "BATTERY":
                batteryEmitter.send(message);
                break;
            case "SOLAR":
                solarEmitter.send(message);
                break;
            case "EV_CHARGER":
                chargerEmitter.send(message);
                break;
            default:
                System.err.println("Unknown asset_type: " + assetType);
        }
    }
}
