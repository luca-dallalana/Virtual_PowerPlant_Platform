# grid-balancing-recommendation

This project uses Quarkus, the Supersonic Subatomic Java Framework.

## Purpose

Compute Grid Balancing Recommendations by analyzing telemetry per grid zone and comparing against zone capacity. If a zone exceeds its safety threshold, the service looks for surplus in other zones and emits a recommendation event.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

> **_NOTE:_** Quarkus Dev UI is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an uber-jar, execute:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

## Related Guides

- Reactive MySQL client: https://quarkus.io/guides/reactive-sql-clients
- SmallRye OpenAPI: https://quarkus.io/guides/openapi-swaggerui
- Kafka with Reactive Messaging: https://quarkus.io/guides/kafka-reactive-getting-started
