package cl.duoc.barriodigital.audit.messaging;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Sobre JSON de audit.timeline (mensajería). */
public record TimelineEnvelope(
        UUID eventId,
        String eventType,
        Integer schemaVersion,
        Instant occurredAt,
        String producer,
        UUID traceId,
        TimelineActor actor,
        TimelinePayload payload
) {
    public record TimelineActor(String id, String name, String role, String sourceIp) {
    }

    public record TimelinePayload(
            String resourceType,
            String resourceId,
            String action,
            JsonNode before,
            JsonNode after
    ) {
    }
}
