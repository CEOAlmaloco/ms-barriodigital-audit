# ms-barriodigital-audit

Timeline de **auditoría** (BarrioDigital). Consume eventos de `audit.timeline`.

Puerto: **8087**

## Persistencia

Oracle Autonomous, el mismo patrón que `ms-barriodigital-requests`: `.env` local, wallet fuera del repo, Hibernate con `ddl-auto: update`.

Dependencias base: Spring Boot 3.3.5, Java 21, `spring-kafka`, `data-jpa`, `web`, `actuator` y driver Oracle (+ wallet).

## Arranque

1. Copiá `.env.example` a `.env` y completá Oracle (podés copiar los valores de requests).
2. El wallet queda **fuera** del repo.

```powershell
copy .env.example .env
.\mvnw.cmd spring-boot:run
```

```powershell
curl http://localhost:8087/actuator/health
```

`GET /actuator/health` responde `{"status":"UP"}` cuando Oracle conectó. El health de Kafka está desactivado: Kafka caído no tumba el servicio.

Tests locales (H2, sin Oracle):

```powershell
.\mvnw.cmd test
```

## Docker

Puerto **8087**. La imagen no lleva el wallet ni el `.env`: el wallet entra como volumen de solo lectura y Oracle llega por variables.

| Variable | Dentro del contenedor |
|---|---|
| `ORACLE_URL` | `jdbc:oracle:thin:@<alias>?TNS_ADMIN=/wallet` |
| `ORACLE_USER` | Usuario de la aplicación |
| `ORACLE_PASSWORD` | Contraseña de ese usuario |
| `ORACLE_WALLET_DIR` | `/wallet` |
| `KAFKA_BOOTSTRAP_SERVERS` | Brokers de Kafka |

```powershell
docker build -t bd-audit .

docker run --rm -p 8087:8087 `
  -e ORACLE_URL="jdbc:oracle:thin:@barriodig_low?TNS_ADMIN=/wallet" `
  -e ORACLE_USER=barriodigital `
  -e ORACLE_PASSWORD=******** `
  -e ORACLE_WALLET_DIR=/wallet `
  -e KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 `
  -v "C:/ruta/a/Wallet_BARRIODIG:/wallet:ro" `
  bd-audit
```

## Git

`.gitignore` cubre `target/`, `.env` y el wallet. Los secretos solo van en `.env`.
