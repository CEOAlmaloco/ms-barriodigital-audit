package cl.duoc.barriodigital.audit.messaging;

import cl.duoc.barriodigital.audit.domain.AuditTrailService;
import cl.duoc.barriodigital.audit.domain.RequestAuditCommand;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listener de requests.events (grupo audit-group, concurrencia 3).
 * El offset se confirma al retornar (AckMode.RECORD), después del commit en Oracle.
 */
@Component
public class RequestEventsListener {

    public static final String TOPIC = "requests.events";
    public static final String GROUP = "audit-group";
    static final String CONSUMER = "audit";

    private static final Logger log = LoggerFactory.getLogger(RequestEventsListener.class);

    private final RequestEventMapper mapper;
    private final AuditTrailService auditTrailService;

    public RequestEventsListener(RequestEventMapper mapper, AuditTrailService auditTrailService) {
        this.mapper = mapper;
        this.auditTrailService = auditTrailService;
    }

    @KafkaListener(
            topics = TOPIC,
            groupId = GROUP,
            concurrency = "3",
            containerFactory = "requestEventsKafkaListenerContainerFactory"
    )
    public void onMessage(ConsumerRecord<String, String> record) {
        String payload = record.value();
        String eventType = mapper.peekEventType(payload);
        if (eventType != null && !RequestEventTypes.isKnown(eventType)) {
            String eventId = mapper.peekEventId(payload);
            log.warn(
                    "evento-desconocido topic={} consumidor={} eventType={} eventId={}",
                    TOPIC,
                    CONSUMER,
                    eventType,
                    eventId != null ? eventId : "desconocido");
            return;
        }

        RequestAuditCommand command = mapper.toCommand(payload);
        try {
            auditTrailService.recordRequestEvent(command);
        } catch (DataIntegrityViolationException ex) {
            if (!DuplicateEvents.isDuplicate(ex)) {
                throw ex;
            }
            log.info(
                    "duplicado topic={} consumidor={} eventId={}",
                    TOPIC,
                    CONSUMER,
                    command.eventId());
        }
    }
}
