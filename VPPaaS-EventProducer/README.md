# VPPaaSSimulator
The Enterprise Integration 2026 tool acts as an event producer for VPPaaS, using a Kafka client library. 

The tool starts by discovering all topics available in the Kafka cluster. For each topic, it queries the VPPaaS API to resolve the prosumer's assets and the utility operator's grid cells, then produces typed telemetry messages matching the actual asset type (BATTERY, SOLAR, EV_CHARGER).

Topics must be named starting with the numeric AssetLink ID followed by a hyphen (e.g. `1-telemetry`, `3-battery-feed`). Topics that do not follow this convention are skipped.

You are free to customize the tool accordingly with your needs. If so, create a pull request to change this repository.

Verify if JAVA 17 is available using the command: 

```
java -version
```

Then, to execute the generator of messages use the following command from the target directory:
```
java -jar VPPaaSSimulator.jar 
```
```
The usage of the Message Producer for VPPaaS 2026, for Enterprise Integration 2026 course, is the following.

VPPaaSSimulator --broker-list <brokers> --throughput <value> --filterprefix <value> --api-base-url <url>
where, 
--broker-list: is a broker list with ports (e.g.: kafka02.example.com:9092,kafka03.example.com:9092), default value is localhost:9092
--throughput: is the approximate maximum messages to be produced by minute, default value is 10
--filterprefix: is the prefix to be filtered. Only the topics starting with this prefix will be considered to sending messages.
--api-base-url: base URL of the VPPaaS API gateway used to resolve asset and grid cell metadata (e.g.: http://my-kong-host:8000), default value is http://localhost:8000
```

Examples of Telemetry_Event messages sent, in JSON, to the discovered topic in Kafka:
```
{
 "timeStamp":"2026-02-20 13:39:53.401",
 "asset_type":"BATTERY",
 "asset_id":"560987123",
 "grid_cell_id":"AUSTIN-DT",
 "payload": {
	"energy_available_kwh":13.936685931917559,
	"max_discharge_power_kw":2.947349764012512,
	"active_power_kw":-7.220102938275808,
	"connection_status":"MAINTENANCE",
	"soh_percent":31.943940694715046,
	"soc_percent":92.22976493508132
 }
}
```
```json
{
 "timeStamp":"2026-02-20 13:40:00.000",
 "asset_type":"SOLAR",
 "asset_id":"1002",
 "grid_cell_id":"LISBON-DT",
 "payload": {
	"generation_kw":5.2,
	"daily_yield_kwh":42.1,
	"ac_voltage_v":230.0,
	"grid_frequency_hz":50.0
 }
}
```
```json
{
 "timeStamp":"2026-02-20 13:40:10.000",
 "asset_type":"EV_CHARGER",
 "asset_id":"1003",
 "grid_cell_id":"LISBON-DT",
 "payload": {
	"connector_status":"CHARGING",
	"charging_power_kw":11.5,
	"session_energy_kwh":8.3,
	"ev_soc_percent":72.0
 }
}
```
