package cl.duoc.barriodigital.audit.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Sobre JSON de requests.events (mensajería). */
public record RequestEventEnvelope(
        UUID eventId,
        String eventType,
        Integer schemaVersion,
        Instant occurredAt,
        String producer,
        UUID traceId,
        Long sequence,
        RequestActor actor,
        JsonNode payload
) {
    public record RequestActor(String id, String name, String role) {
    }
}
