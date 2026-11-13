package cl.duoc.barriodigital.audit.messaging;

import cl.duoc.barriodigital.audit.domain.AuditSummaryFactory;
import cl.duoc.barriodigital.audit.domain.RequestAuditCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Valida el JSON de requests.events y lo convierte a comando de dominio.
 * Un mensaje malformado o con schemaVersion no soportada lanza NonRetryableEventException.
 */
@Component
public class RequestEventMapper {

    public static final int SUPPORTED_SCHEMA_VERSION = 1;
    public static final String PRODUCER = "requests";

    private static final Set<String> ROLES = Set.of("Admin", "Funcionario", "Vecino", "Auditor");
    private static final Pattern FOLIO = Pattern.compile("BD-\\d{4}-\\d{6}");

    private final ObjectMapper objectMapper;

    public RequestEventMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Solo lee eventId; null si el JSON no se puede parsear. */
    String peekEventId(String json) {
        return peek(json, "eventId");
    }

    String peekEventType(String json) {
        return peek(json, "eventType");
    }

    public RequestAuditCommand toCommand(String json) {
        RequestEventEnvelope envelope = parse(json);
        validate(envelope);
        JsonNode payload = envelope.payload();
        String requestId = text(payload, "requestId");
        String folio = text(payload, "folio");
        if (!isUuid(requestId)) {
            throw new NonRetryableEventException("Payload invalido: requestId");
        }
        if (folio == null || !FOLIO.matcher(folio).matches()) {
            throw new NonRetryableEventException("Payload invalido: folio");
        }
        String fromStatus = null;
        String toStatus = null;
        if ("request.status-changed".equals(envelope.eventType())) {
            fromStatus = text(payload, "fromStatus");
            toStatus = text(payload, "toStatus");
            if (!StringUtils.hasText(fromStatus) || !StringUtils.hasText(toStatus)) {
                throw new NonRetryableEventException("Payload invalido: fromStatus y toStatus");
            }
        }
        return new RequestAuditCommand(
                envelope.eventId(),
                envelope.eventType(),
                envelope.occurredAt(),
                envelope.producer(),
                envelope.actor().id(),
                envelope.actor().name(),
                envelope.actor().role(),
                UUID.fromString(requestId),
                folio,
                envelope.sequence(),
                AuditSummaryFactory.forRequest(envelope.eventType(), folio, fromStatus, toStatus),
                writeJson(payload)
        );
    }

    private String peek(String json, String field) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception ex) {
            return null;
        }
    }

    private RequestEventEnvelope parse(String json) {
        if (!StringUtils.hasText(json)) {
            throw new NonRetryableEventException("JSON invalido: cuerpo vacio");
        }
        try {
            return objectMapper.readValue(json, RequestEventEnvelope.class);
        } catch (JsonProcessingException ex) {
            throw new NonRetryableEventException("JSON invalido", ex);
        }
    }

    private void validate(RequestEventEnvelope envelope) {
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
        if (!PRODUCER.equals(envelope.producer())) {
            throw new NonRetryableEventException("Sobre invalido: producer");
        }
        if (envelope.traceId() == null) {
            throw new NonRetryableEventException("Sobre invalido: falta traceId");
        }
        if (envelope.sequence() == null || envelope.sequence() < 1) {
            throw new NonRetryableEventException("Sobre invalido: sequence");
        }
        if (envelope.actor() == null
                || !StringUtils.hasText(envelope.actor().id())
                || !StringUtils.hasText(envelope.actor().name())
                || !ROLES.contains(envelope.actor().role())) {
            throw new NonRetryableEventException("Sobre invalido: actor");
        }
        if (envelope.payload() == null || !envelope.payload().isObject()) {
            throw new NonRetryableEventException("Sobre invalido: falta payload");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private static boolean isUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            throw new NonRetryableEventException("Payload invalido", ex);
        }
    }
}
