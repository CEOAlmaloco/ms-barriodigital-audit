package cl.duoc.barriodigital.audit.messaging;

import cl.duoc.barriodigital.audit.domain.AuditTrailService;
import cl.duoc.barriodigital.audit.domain.TimelineAuditCommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.kafka.annotation.KafkaListener;

/**
 * Listener propio de audit.timeline (grupo audit-group, concurrencia 3).
 * Valida y delega; el dominio no conoce Kafka.
 */
@Component
public class AuditTimelineListener {

    public static final String TOPIC = "audit.timeline";
    public static final String GROUP = "audit-group";

    private static final Logger log = LoggerFactory.getLogger(AuditTimelineListener.class);

    private final TimelineEventMapper mapper;
    private final AuditTrailService auditTrailService;
    private final ObjectMapper objectMapper;

    public AuditTimelineListener(
            TimelineEventMapper mapper,
            AuditTrailService auditTrailService,
            ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.auditTrailService = auditTrailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP,
            concurrency = "3",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String payload = record.value();
        String eventType = peek(payload, "eventType");
        String eventId = peek(payload, "eventId");

        if (eventType != null && !TimelineEventTypes.isKnown(eventType)) {
            log.warn(
                    "evento-desconocido topic={} consumidor=audit eventType={} eventId={}",
                    TOPIC,
                    eventType,
                    eventId != null ? eventId : "desconocido");
            return;
        }

        TimelineAuditCommand command = mapper.toCommand(payload);
        try {
            auditTrailService.recordTimelineEvent(command);
        } catch (DataIntegrityViolationException ex) {
            if (!DuplicateEvents.isDuplicate(ex)) {
                throw ex;
            }
            log.info(
                    "duplicado topic={} consumidor=audit eventId={}",
                    TOPIC,
                    command.eventId());
        }
    }

    private String peek(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json).get(field);
            return node == null || node.isNull() ? null : node.asText();
        } catch (Exception ex) {
            return null;
        }
    }
}
