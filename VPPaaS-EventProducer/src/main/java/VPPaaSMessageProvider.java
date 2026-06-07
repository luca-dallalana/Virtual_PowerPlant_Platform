import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Map;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.json.JSONArray;
import org.json.JSONObject;


public class VPPaaSMessageProvider {

	static String brokerList = "localhost:9092";
	static int throughput = 10;
	static String filterprefix = "";
	static String apiBaseUrl = "http://localhost:8000";

	static Map<String, List<PartitionInfo>> topics;
	static Map<String, TopicMetadata> topicMetadata = new HashMap<>();

	static class AssetInfo {
		long assetId;
		String assetType;
		AssetInfo(long assetId, String assetType) {
			this.assetId = assetId;
			this.assetType = assetType;
		}
	}

	static class TopicMetadata {
		List<AssetInfo> assets;
		List<String> gridCellIds;
		TopicMetadata(List<AssetInfo> assets, List<String> gridCellIds) {
			this.assets = assets;
			this.gridCellIds = gridCellIds;
		}
	}

	private static String RandomTopic()
	{
		String Topic = new String("");
		int index = (new Random()).nextInt(topics.size());
		Set<String> keys = topics.keySet();
		Iterator<String> it = keys.iterator();
		for (int idx = 0; idx < index; idx++) it.next();
		Topic = (String) it.next();
		System.out.println("Topic randomized: " + Topic);
		return Topic;
	}

	private static String httpGet(String url) throws Exception {
		HttpClient httpClient = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.GET()
			.build();
		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new Exception("HTTP " + response.statusCode() + " from " + url);
		}
		return response.body();
	}

	private static TopicMetadata resolveTopicMetadata(String topic) {
		try {
			String assetLinkIdStr = topic.substring(0, topic.indexOf('-'));
			long assetLinkId = Long.parseLong(assetLinkIdStr);

			// AssetLink -> idProsumer, idUtilityOperator
			String assetLinkJson = httpGet(apiBaseUrl + "/AssetLink/" + assetLinkId);
			JSONObject assetLinkObj = new JSONObject(assetLinkJson);
			long idProsumer = assetLinkObj.getLong("idProsumer");
			long idUtilityOperator = assetLinkObj.getLong("idUtilityOperator");

			// Prosumer's assets
			String assetsJson = httpGet(apiBaseUrl + "/Prosumer/" + idProsumer + "/assets");
			JSONArray assetsArray = new JSONArray(assetsJson);
			List<AssetInfo> assets = new ArrayList<>();
			for (int i = 0; i < assetsArray.length(); i++) {
				JSONObject assetObj = assetsArray.getJSONObject(i);
				assets.add(new AssetInfo(assetObj.getLong("assetId"), assetObj.getString("assetType")));
			}

			// UtilityOperator's GridCells
			String gridCellsJson = httpGet(apiBaseUrl + "/UtilityOperator/" + idUtilityOperator + "/gridcells");
			JSONArray gridCellsArray = new JSONArray(gridCellsJson);
			List<String> gridCellIds = new ArrayList<>();
			for (int i = 0; i < gridCellsArray.length(); i++) {
				gridCellIds.add(gridCellsArray.getJSONObject(i).getString("gridCellId"));
			}

			if (assets.isEmpty() || gridCellIds.isEmpty()) {
				System.out.println("Skipping topic " + topic + ": no assets or no grid cells found.");
				return null;
			}

			System.out.println("Resolved topic " + topic + ": " + assets.size() + " asset(s), " + gridCellIds.size() + " grid cell(s).");
			return new TopicMetadata(assets, gridCellIds);
		} catch (NumberFormatException e) {
			System.out.println("Topic " + topic + " does not start with a numeric AssetLink ID, skipping.");
			return null;
		} catch (Exception e) {
			System.out.println("Could not resolve metadata for topic " + topic + ": " + e.getMessage());
			return null;
		}
	}

	private static Message CreateMessage(String topicToSend, Timestamp ts)
	{
		if (!topicToSend.contains("-")) return null;

		TopicMetadata meta = topicMetadata.get(topicToSend);
		if (meta == null || meta.assets.isEmpty() || meta.gridCellIds.isEmpty()) {
			System.out.println("No resolved metadata for topic: " + topicToSend + ", skipping.");
			return null;
		}

		AssetInfo asset = meta.assets.get(new Random().nextInt(meta.assets.size()));
		String gridCellId = meta.gridCellIds.get(new Random().nextInt(meta.gridCellIds.size()));
		String assetIdStr = String.valueOf(asset.assetId);

		switch (asset.assetType) {
			case "BATTERY":
				return new BatteryEnergyStorage(ts.toLocalDateTime(), assetIdStr, gridCellId,
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 100.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 20.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(-10.0, 10.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 5.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 100.0)),
					new BatteryEnergyStorage().randomLevel());
			case "SOLAR":
				return new SolarInverter(ts.toLocalDateTime(), assetIdStr, gridCellId,
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 7.5)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 150.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(245.0, 255.0)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(49.5, 50.5)));
			case "EV_CHARGER":
				return new EVCharger(ts.toLocalDateTime(), assetIdStr, gridCellId,
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 20.5)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 17.8)),
					Double.valueOf(ThreadLocalRandom.current().nextDouble(0.0, 100.0)),
					new EVCharger().randomLevel());
			default:
				System.out.println("Unknown asset type '" + asset.assetType + "' for asset " + asset.assetId + ", skipping.");
				return null;
		}
	}

	private static void CheckArguments()
	{
		System.out.println(
						 "--broker-list=" + brokerList + "\n" +
						 "--throughput=" + throughput + "\n" +
						 "--filterprefix=" + filterprefix + "\n" +
						 "--api-base-url=" + apiBaseUrl);
	}

	private static boolean VerifyArgs(String[] cabecalho)
	{
		for (int i = 0; i < cabecalho.length; i = i + 2)
		{
			if (cabecalho[i].compareTo("--broker-list") == 0) brokerList = cabecalho[i+1];
			else if (cabecalho[i].compareTo("--throughput") == 0) throughput = Integer.valueOf(cabecalho[i+1]).intValue();
			else if (cabecalho[i].compareTo("--filterprefix") == 0) filterprefix = cabecalho[i+1];
			else if (cabecalho[i].compareTo("--api-base-url") == 0) apiBaseUrl = cabecalho[i+1];
			else
			{
				System.out.println("Bad argument name: " + cabecalho[i]);
				return(false);
			}
		}

		if (brokerList.length() == 0) System.out.println("Broker-list argument is mandatory!");
		else return(true);

		return(false);
	}

	private static void SendMessage(Message msg, KafkaProducer<String, String> prd, String topicTarget)
	{
		System.out.println("This is the message to send = " + msg.toStringAsJSON());
		String seqkey = new String("");
		seqkey = msg.getSeqkey().toString();
		System.out.println("Sending new message to Kafka, to the topic = " + topicTarget + ", with key=" + seqkey);
		ProducerRecord<String, String> record = new ProducerRecord<>(topicTarget, seqkey, msg.toStringAsJSON());
		prd.send(record);
		System.out.print("Sent...Fire-and-forget stopped...");
	}

	private static void CheckTopicsAvailable()
	{
		/*** check all topics in kafka cluster from JAVA  ******/
		Properties props = new Properties();
		props.put("bootstrap.servers", brokerList);
		props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
		props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

		KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
		topics = consumer.listTopics();
		consumer.close();

		topics.remove("__consumer_offsets");
		System.out.print("Topics discovered = { ");
		for (String topicName : topics.keySet()) System.out.print(topicName + " ; ");
		System.out.println(" } ");
		/******************************************************/

		// Purge metadata for topics that no longer exist
		topicMetadata.keySet().retainAll(topics.keySet());

		// Resolve metadata for newly discovered topics
		for (String topicName : topics.keySet()) {
			if (topicName.startsWith(filterprefix) && topicName.contains("-") && !topicMetadata.containsKey(topicName)) {
				TopicMetadata meta = resolveTopicMetadata(topicName);
				if (meta != null) topicMetadata.put(topicName, meta);
			}
		}
	}

	public static void main(String[] args) {

		String usage = "The usage of the Message Producer for VPPaaS 2026, for Enterprise Integration 2026 course, is the following.\n" +
						"\n" +
						"VPPaaSSimulator --broker-list <brokers> --throughput <value> --filterprefix <value> --api-base-url <url>\n" +
						"where, \n" +
						"--broker-list: is a broker list with ports (e.g.: kafka02.example.com:9092,kafka03.example.com:9092), default value is localhost:9092\n" +
						"--throughput: is the approximate maximum messages to be produced by minute, default value is 10\n" +
						"--filterprefix: is the prefix to be filtered. Only the topics starting with this prefix will be considered to sending messages.\n" +
						"--api-base-url: base URL of the VPPaaS REST API gateway (e.g.: http://my-kong-host:8000), default value is http://localhost:8000\n";

		Properties kafkaProps = new Properties();
		if (args.length == 0) System.out.println(usage);
		else
		{
			if (VerifyArgs(args))
			{
				System.out.println("The following arguments are accepted:");
				CheckArguments();
				System.out.println("------- Processing starting -------");

				kafkaProps.put("bootstrap.servers", brokerList);
				kafkaProps.put("key.serializer","org.apache.kafka.common.serialization.StringSerializer");
				kafkaProps.put("value.serializer","org.apache.kafka.common.serialization.StringSerializer");
				KafkaProducer<String, String> producer = new KafkaProducer<String, String>(kafkaProps);

				CheckTopicsAvailable();

				Timestamp mili;

				while (true)
				{
					try {
						mili = new Timestamp(System.currentTimeMillis());

						if (!topics.isEmpty())
						{
							String topic_to_send = RandomTopic();

							if (topic_to_send.startsWith(filterprefix))
							{
								Message messageToSend = CreateMessage(topic_to_send, mili);
								if (messageToSend != null) SendMessage(messageToSend, producer, topic_to_send);
							}
							else System.out.println("Topic = " + topic_to_send + " has been filtered. Therefore, not sending message.");
						}
						else System.out.println("Empty list of Topics. Therefore, no message to send.");

						Timestamp timestamp = new Timestamp(System.currentTimeMillis());
						System.out.println("...Time spent for sending: " + (timestamp.getTime() - mili.getTime()));
						Thread.sleep(60000/throughput);
						CheckTopicsAvailable();
					}
					catch (Exception e) { e.printStackTrace(); }
				}
			}
			else System.out.println("Application Arguments bad usage.\n\nPlease check syntax.\n\n" + usage);
		}
	}
}
