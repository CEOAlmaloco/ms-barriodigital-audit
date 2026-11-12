package cl.duoc.barriodigital.audit.messaging;

import cl.duoc.barriodigital.audit.domain.AuditSummaryFactory;
import cl.duoc.barriodigital.audit.domain.TimelineAuditCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Valida el JSON de audit.timeline y lo convierte a comando de dominio.
 */
@Component
public class TimelineEventMapper {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final String RESULT_SUCCESS = "SUCCESS";

    private final ObjectMapper objectMapper;

    public TimelineEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Solo lee eventId; null si el JSON no se puede parsear. */
    String peekEventId(String json) {
        try {
            JsonNode node = objectMapper.readTree(json).get("eventId");
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    public TimelineAuditCommand toCommand(String json) {
        TimelineEnvelope envelope = parse(json);
        validate(envelope);
        TimelineEnvelope.TimelinePayload payload = envelope.payload();
        TimelineEnvelope.TimelineActor actor = envelope.actor();
        return new TimelineAuditCommand(
                envelope.eventId(),
                envelope.eventType(),
                envelope.occurredAt(),
                envelope.producer(),
                actor.id(),
                actor.name(),
                actor.role(),
                actor.sourceIp(),
                payload.resourceType(),
                payload.resourceId(),
                payload.action(),
                writeJson(payload.before()),
                writeJson(payload.after()),
                RESULT_SUCCESS,
                AuditSummaryFactory.forTimeline(payload.resourceType(), payload.resourceId(), payload.action())
        );
    }

    private TimelineEnvelope parse(String json) {
        if (!StringUtils.hasText(json)) {
            throw new NonRetryableEventException("JSON invalido: cuerpo vacio");
        }
        try {
            return objectMapper.readValue(json, TimelineEnvelope.class);
        } catch (JsonProcessingException ex) {
            throw new NonRetryableEventException("JSON invalido", ex);
        }
    }

    private void validate(TimelineEnvelope envelope) {
        if (envelope.eventId() == null) {
            throw new NonRetryableEventException("Sobre invalido: falta eventId");
        }
        if (!StringUtils.hasText(envelope.eventType())) {
            throw new NonRetryableEventException("Sobre invalido: falta eventType");
        }
        if (envelope.schemaVersion() == null) {
            throw new NonRetryableEventException("Sobre invalido: falta schemaVersion");
        }
        if (envelope.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new NonRetryableEventException(
                    "schemaVersion no soportada: " + envelope.schemaVersion());
        }
        if (envelope.occurredAt() == null) {
            throw new NonRetryableEventException("Sobre invalido: falta occurredAt");
        }
        if (!StringUtils.hasText(envelope.producer())) {
            throw new NonRetryableEventException("Sobre invalido: falta producer");
        }
        if (envelope.actor() == null) {
            throw new NonRetryableEventException("Sobre invalido: falta actor");
        }
        if (!StringUtils.hasText(envelope.actor().id())
                || !StringUtils.hasText(envelope.actor().name())
                || !StringUtils.hasText(envelope.actor().role())) {
            throw new NonRetryableEventException("Sobre invalido: actor incompleto");
        }
        TimelineEnvelope.TimelinePayload payload = envelope.payload();
        if (payload == null) {
            throw new NonRetryableEventException("Sobre invalido: falta payload");
        }
        if (!StringUtils.hasText(payload.resourceType())
                || !StringUtils.hasText(payload.resourceId())
                || !StringUtils.hasText(payload.action())) {
            throw new NonRetryableEventException("Payload invalido: resourceType/resourceId/action");
        }
        String expectedType = payload.resourceType() + "." + payload.action();
        if (!expectedType.equals(envelope.eventType())) {
            throw new NonRetryableEventException(
                    "eventType incompatible con payload: " + envelope.eventType());
        }
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new NonRetryableEventException("Payload invalido: before/after", ex);
        }
    }
}
