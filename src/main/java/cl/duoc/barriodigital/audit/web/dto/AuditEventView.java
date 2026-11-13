package cl.duoc.barriodigital.audit.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Ítem de timeline o búsqueda (EP3-07 §5.2). */
public record AuditEventView(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String producer,
        ActorView actor,
        String resourceType,
        String resourceId,
        UUID requestId,
        String folio,
        Long sequence,
        String summary,
        JsonNode details
) {
    public record ActorView(String id, String name, String role, String sourceIp) {
    }
}
