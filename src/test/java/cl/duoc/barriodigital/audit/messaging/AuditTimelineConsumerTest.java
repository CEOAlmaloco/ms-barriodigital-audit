package cl.duoc.barriodigital.audit.messaging;

import cl.duoc.barriodigital.audit.domain.AuditEntry;
import cl.duoc.barriodigital.audit.domain.AuditEntryRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Consume eventos de catalog, mq-admin y kafka-admin (EP3-41).
 */
@SpringBootTest
class AuditTimelineConsumerTest {

    @Autowired
    private AuditTimelineListener listener;

    @Autowired
    private AuditEntryRepository repository;

    @Autowired
    private TimelineEventMapper mapper;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void persisteEventoDeCatalog() {
        UUID eventId = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");
        listener.onMessage(record(catalogUpdatedJson(eventId)));

        AuditEntry entry = repository.findByEventId(eventId).orElseThrow();
        assertThat(entry.getEventType()).isEqualTo("procedure-type.updated");
        assertThat(entry.getProducer()).isEqualTo("catalog");
        assertThat(entry.getResourceType()).isEqualTo("procedure-type");
        assertThat(entry.getResourceId()).isEqualTo("bache");
        assertThat(entry.getAction()).isEqualTo("updated");
        assertThat(entry.getSourceIp()).isEqualTo("203.0.113.25");
        assertThat(entry.getResult()).isEqualTo("SUCCESS");
        assertThat(entry.getBeforeJson()).contains("\"dailyQuota\":20");
        assertThat(entry.getAfterJson()).contains("\"dailyQuota\":15");
        assertThat(entry.getSummary()).isEqualTo("Edicion del tipo de tramite bache");
        assertThat(entry.getActorRole()).isEqualTo("Admin");
    }

    @Test
    void persisteEventoDeMqAdmin() {
        UUID eventId = UUID.fromString("7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d9e0f");
        listener.onMessage(record(mqAdminPurgedJson(eventId)));

        AuditEntry entry = repository.findByEventId(eventId).orElseThrow();
        assertThat(entry.getEventType()).isEqualTo("queue.purged");
        assertThat(entry.getProducer()).isEqualTo("mq-admin");
        assertThat(entry.getResourceType()).isEqualTo("queue");
        assertThat(entry.getResourceId()).isEqualTo("q.demo.avisos");
        assertThat(entry.getAction()).isEqualTo("purged");
        assertThat(entry.getSourceIp()).isEqualTo("203.0.113.25");
        assertThat(entry.getResult()).isEqualTo("SUCCESS");
        assertThat(entry.getBeforeJson()).contains("\"messages\":12");
        assertThat(entry.getAfterJson()).contains("\"messagesRemoved\":12");
        assertThat(entry.getSummary()).isEqualTo("Purga de la cola q.demo.avisos");
    }

    @Test
    void persisteEventoDeKafkaAdmin() {
        UUID eventId = UUID.fromString("a1b2c3d4-e5f6-4789-a012-3456789abcde");
        listener.onMessage(record(kafkaAdminTopicUpdatedJson(eventId)));

        AuditEntry entry = repository.findByEventId(eventId).orElseThrow();
        assertThat(entry.getEventType()).isEqualTo("topic.updated");
        assertThat(entry.getProducer()).isEqualTo("kafka-admin");
        assertThat(entry.getResourceType()).isEqualTo("topic");
        assertThat(entry.getResourceId()).isEqualTo("demo.events");
        assertThat(entry.getAction()).isEqualTo("updated");
        assertThat(entry.getSourceIp()).isEqualTo("203.0.113.25");
        assertThat(entry.getResult()).isEqualTo("SUCCESS");
        assertThat(entry.getBeforeJson()).contains("\"partitions\":3");
        assertThat(entry.getAfterJson()).contains("\"partitions\":6");
        assertThat(entry.getSummary()).isEqualTo("Actualizacion del topico demo.events");
    }

    @Test
    void duplicadoNoInsertaSegundaFila() {
        UUID eventId = UUID.fromString("5e6f7a8b-9c0d-4e1f-8a2b-3c4d5e6f7a8b");
        listener.onMessage(record(catalogUpdatedJson(eventId)));
        listener.onMessage(record(catalogUpdatedJson(eventId)));

        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void eventTypeDesconocidoNoPersiste() {
        String json = """
                {
                  "eventId": "0b1c2d3e-4f5a-4b6c-8d7e-9f0a1b2c3d4e",
                  "eventType": "queue.rated",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-12T12:20:31.774Z",
                  "producer": "mq-admin",
                  "traceId": "ee55ff66-0011-4223-9445-566778899aab",
                  "actor": { "id": "u1", "name": "Admin", "role": "Admin", "sourceIp": null },
                  "payload": {
                    "resourceType": "queue",
                    "resourceId": "q.x",
                    "action": "rated",
                    "before": null,
                    "after": null
                  }
                }
                """;
        listener.onMessage(record(json));
        assertThat(repository.count()).isZero();
    }

    @Test
    void malformadoEsNoReintentable() {
        assertThatThrownBy(() -> mapper.toCommand("{no-json"))
                .isInstanceOf(NonRetryableEventException.class)
                .hasMessageContaining("JSON invalido");
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>(AuditTimelineListener.TOPIC, 0, 0L, "procedure-type:bache", json);
    }

    private static String catalogUpdatedJson(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "procedure-type.updated",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-12T12:20:31.774Z",
                  "producer": "catalog",
                  "traceId": "ee55ff66-0011-4223-9445-566778899aab",
                  "actor": {
                    "id": "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d",
                    "name": "Admin Test",
                    "role": "Admin",
                    "sourceIp": "203.0.113.25"
                  },
                  "payload": {
                    "resourceType": "procedure-type",
                    "resourceId": "bache",
                    "action": "updated",
                    "before": { "label": "Bache en calle", "active": true, "dailyQuota": 20, "slaBusinessDays": null },
                    "after": { "label": "Bache en calle", "active": true, "dailyQuota": 15, "slaBusinessDays": 5 }
                  }
                }
                """.formatted(eventId);
    }

    private static String mqAdminPurgedJson(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "queue.purged",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-12T12:45:08.003Z",
                  "producer": "mq-admin",
                  "traceId": "ff667700-1122-4334-8556-6778899aabbc",
                  "actor": {
                    "id": "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d",
                    "name": "Admin Test",
                    "role": "Admin",
                    "sourceIp": "203.0.113.25"
                  },
                  "payload": {
                    "resourceType": "queue",
                    "resourceId": "q.demo.avisos",
                    "action": "purged",
                    "before": { "messages": 12 },
                    "after": { "messagesRemoved": 12 }
                  }
                }
                """.formatted(eventId);
    }

    private static String kafkaAdminTopicUpdatedJson(UUID eventId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "topic.updated",
                  "schemaVersion": 1,
                  "occurredAt": "2026-11-12T13:00:00.000Z",
                  "producer": "kafka-admin",
                  "traceId": "aa11bb22-3344-4555-9666-778899aabbcc",
                  "actor": {
                    "id": "2a3b4c5d-6e7f-4a8b-9c0d-1e2f3a4b5c6d",
                    "name": "Admin Test",
                    "role": "Admin",
                    "sourceIp": "203.0.113.25"
                  },
                  "payload": {
                    "resourceType": "topic",
                    "resourceId": "demo.events",
                    "action": "updated",
                    "before": { "partitions": 3 },
                    "after": { "partitions": 6 }
                  }
                }
                """.formatted(eventId);
    }
}
