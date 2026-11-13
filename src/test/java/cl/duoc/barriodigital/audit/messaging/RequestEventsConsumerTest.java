package cl.duoc.barriodigital.audit.messaging;

import cl.duoc.barriodigital.audit.domain.AuditEntry;
import cl.duoc.barriodigital.audit.domain.AuditEntryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consume requests.events: persistencia, duplicado, malformado y desconocido (EP3-40).
 */
@SpringBootTest
class RequestEventsConsumerTest {

    private static final UUID EVENT_ID = UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private static final UUID REQUEST_ID = UUID.fromString("7b6e1d44-2a18-4c90-9f01-88aa11bb22cc");

    @Autowired
    private RequestEventsListener listener;

    @Autowired
    private RequestEventMapper mapper;

    @Autowired
    private AuditEntryRepository repository;

    @Autowired
    private ConcurrentKafkaListenerContainerFactory<?, ?> requestEventsKafkaListenerContainerFactory;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void persisteEventoDeTramite() {
        listener.onMessage(record(statusChangedJson(EVENT_ID)));

        AuditEntry entry = repository.findByEventId(EVENT_ID).orElseThrow();
        assertThat(entry.getEventType()).isEqualTo("request.status-changed");
        assertThat(entry.getActorId()).isEqualTo("8e7f6a5b-4c3d-4e2f-9a1b-0c9d8e7f6a5b");
        assertThat(entry.getActorRole()).isEqualTo("Funcionario");
        assertThat(entry.getResourceType()).isEqualTo("request");
        assertThat(entry.getRequestId()).isEqualTo(REQUEST_ID);
        assertThat(entry.getFolio()).isEqualTo("BD-2026-000123");
        assertThat(entry.getSequence()).isEqualTo(1057L);
        assertThat(entry.getSummary()).isEqualTo("Cambio de estado de ADMITIDO a RECHAZADO");
        assertThat(entry.getDetailsJson()).contains("\"interventionReason\":null");
        assertThat(entry.getDetailsJson()).contains("Direccion fuera de la comuna");
        assertThat(entry.getOccurredAt()).isEqualTo(Instant.parse("2026-11-10T15:40:03.112Z"));
        assertThat(entry.getSourceIp()).isNull();
    }

    @Test
    void duplicadoNoInsertaSegundaFila() {
        listener.onMessage(record(statusChangedJson(EVENT_ID)));
        listener.onMessage(record(statusChangedJson(EVENT_ID)));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void eventTypeDesconocidoNoPersiste() {
        listener.onMessage(record("""
                {
                  "eventId": "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
                  "eventType": "request.deleted",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-10T15:40:03.112Z",
                  "producer": "requests",
                  "traceId": "aa11bb22-cc33-4dd5-8ee6-ff0011223344",
                  "sequence": 1058,
                  "actor": { "id": "u1", "name": "Admin", "role": "Admin" },
                  "payload": { "requestId": "7b6e1d44-2a18-4c90-9f01-88aa11bb22cc", "folio": "BD-2026-000123" }
                }
                """));

        assertThat(repository.count()).isZero();
    }

    @Test
    void malformadoEsNoReintentable() {
        assertThatThrownBy(() -> mapper.toCommand("{no-json"))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("JSON invalido");
    }

    @Test
    void elOffsetSeConfirmaAlTerminarElListener() {
        assertThat(requestEventsKafkaListenerContainerFactory.getContainerProperties().getAckMode())
                .isEqualTo(ContainerProperties.AckMode.RECORD);
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>(RequestEventsListener.TOPIC, 0, 0L, REQUEST_ID.toString(), json);
    }

    private static String statusChangedJson(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "request.status-changed",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-10T15:40:03.112Z",
                  "producer": "requests",
                  "traceId": "aa11bb22-cc33-4dd5-8ee6-ff0011223344",
                  "sequence": 1057,
                  "actor": {
                    "id": "8e7f6a5b-4c3d-4e2f-9a1b-0c9d8e7f6a5b",
                    "name": "Funcionario Test",
                    "role": "Funcionario"
                  },
                  "payload": {
                    "requestId": "7b6e1d44-2a18-4c90-9f01-88aa11bb22cc",
                    "folio": "BD-2026-000123",
                    "procedureType": "bache",
                    "sector": "ralco",
                    "fromStatus": "ADMITIDO",
                    "toStatus": "RECHAZADO",
                    "reason": "Direccion fuera de la comuna",
                    "interventionReason": null
                  }
                }
                """.formatted(eventId);
    }
}
