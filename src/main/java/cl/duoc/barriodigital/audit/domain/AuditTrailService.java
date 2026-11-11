package cl.duoc.barriodigital.audit.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persiste eventos de auditoría. No conoce Kafka (EP3-02 §7.4).
 */
@Service
public class AuditTrailService {

    private final AuditEntryRepository repository;

    public AuditTrailService(AuditEntryRepository repository) {
        this.repository = repository;
    }

    /**
     * Guarda un evento de audit.timeline.
     * Un event_id repetido dispara la violación de uk_audit_entries_event_id;
     * el listener la interpreta fuera de esta transacción (EP3-02 §9.2).
     */
    @Transactional
    public void recordTimelineEvent(TimelineAuditCommand command) {
        AuditEntry entry = new AuditEntry();
        entry.setEventId(command.eventId());
        entry.setEventType(command.eventType());
        entry.setOccurredAt(command.occurredAt());
        entry.setProducer(command.producer());
        entry.setActorId(command.actorId());
        entry.setActorName(command.actorName());
        entry.setActorRole(command.actorRole());
        entry.setSourceIp(command.sourceIp());
        entry.setResourceType(command.resourceType());
        entry.setResourceId(command.resourceId());
        entry.setAction(command.action());
        entry.setBeforeJson(command.beforeJson());
        entry.setAfterJson(command.afterJson());
        entry.setResult(command.result());
        entry.setSummary(command.summary());
        repository.saveAndFlush(entry);
    }
}
