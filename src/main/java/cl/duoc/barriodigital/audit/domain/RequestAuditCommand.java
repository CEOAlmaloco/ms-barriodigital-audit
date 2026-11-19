package cl.duoc.barriodigital.audit.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Comando de dominio para un evento de requests.events.
 * Sin tipos de Kafka.
 */
public record RequestAuditCommand(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String producer,
        String actorId,
        String actorName,
        String actorRole,
        UUID requestId,
        String folio,
        Long sequence,
        String summary,
        String detailsJson
) {
}
