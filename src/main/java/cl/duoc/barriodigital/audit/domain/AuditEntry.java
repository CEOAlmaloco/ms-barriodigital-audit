package cl.duoc.barriodigital.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Fila de auditoría (requests.events y audit.timeline).
 * La unicidad de event_id es la marca de idempotencia (EP3-02 §9).
 */
@Entity
@Table(
        name = "audit_entries",
        uniqueConstraints = @UniqueConstraint(name = "uk_audit_entries_event_id", columnNames = "event_id")
)
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false)
    private UUID eventId;

    @Column(name = "event_type", length = 80, nullable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "producer", length = 40, nullable = false)
    private String producer;

    @Column(name = "actor_id", length = 64)
    private String actorId;

    @Column(name = "actor_name", length = 200)
    private String actorName;

    @Column(name = "actor_role", length = 40)
    private String actorRole;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @Column(name = "resource_type", length = 40, nullable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 400, nullable = false)
    private String resourceId;

    @Column(name = "action", length = 40)
    private String action;

    @Lob
    @Column(name = "before_json")
    private String beforeJson;

    @Lob
    @Column(name = "after_json")
    private String afterJson;

    /** Resultado de la operación administrativa (SUCCESS en audit.timeline). */
    @Column(name = "result", length = 40)
    private String result;

    @Column(name = "summary", length = 500)
    private String summary;

    @Column(name = "request_id", length = 36)
    private UUID requestId;

    @Column(name = "folio", length = 32)
    private String folio;

    @Column(name = "sequence_num")
    private Long sequence;

    @Lob
    @Column(name = "details_json")
    private String detailsJson;

    protected AuditEntry() {
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getProducer() {
        return producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    public String getActorId() {
        return actorId;
    }

    public void setActorId(String actorId) {
        this.actorId = actorId;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorRole() {
        return actorRole;
    }

    public void setActorRole(String actorRole) {
        this.actorRole = actorRole;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public void setSourceIp(String sourceIp) {
        this.sourceIp = sourceIp;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public UUID getRequestId() {
        return requestId;
    }

    public void setRequestId(UUID requestId) {
        this.requestId = requestId;
    }

    public String getFolio() {
        return folio;
    }

    public void setFolio(String folio) {
        this.folio = folio;
    }

    public Long getSequence() {
        return sequence;
    }

    public void setSequence(Long sequence) {
        this.sequence = sequence;
    }

    public String getDetailsJson() {
        return detailsJson;
    }

    public void setDetailsJson(String detailsJson) {
        this.detailsJson = detailsJson;
    }
}
