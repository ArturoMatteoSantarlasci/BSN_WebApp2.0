# BSN_WebApp2.0

WebApp Spring Boot per la gestione di campagne e sensori, con integrazione MQTT/SSE e persistenza su MariaDB (e InfluxDB per serie temporali).

## Requisiti
- Java 17+ (consigliato)
- Maven (oppure `./mvnw`)
- MariaDB (puoi usare `docker-compose.yml` se presente/configurato)

## Avvio rapido (sviluppo)
1. Configura le proprietà in `src/main/resources/application.properties`.
2. Avvia l’app:
   - con Maven Wrapper: `./mvnw spring-boot:run`

## Note
- La cartella `target/` è ignorata da Git.
- Configurazioni IDE (`.idea/`, `*.iml`) sono ignorate da Git.
