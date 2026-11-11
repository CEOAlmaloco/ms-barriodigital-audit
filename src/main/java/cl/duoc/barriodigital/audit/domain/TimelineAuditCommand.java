package cl.duoc.barriodigital.audit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando de dominio para un evento de audit.timeline.
 * Sin tipos de Kafka.
 */
public record TimelineAuditCommand(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String producer,
        String actorId,
        String actorName,
        String actorRole,
        String sourceIp,
        String resourceType,
        String resourceId,
        String action,
        String beforeJson,
        String afterJson,
        String result,
        String summary
) {
}
