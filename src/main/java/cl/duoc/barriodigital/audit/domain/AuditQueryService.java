package cl.duoc.barriodigital.audit.domain;

import cl.duoc.barriodigital.audit.web.dto.AuditEventView;
import cl.duoc.barriodigital.audit.web.dto.AuditPageView;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas de solo lectura del timeline y la búsqueda (EP3-07 §5). */
@Service
public class AuditQueryService {

    private static final Sort TIMELINE_SORT = Sort.by(
            Sort.Order.asc("occurredAt"),
            Sort.Order.asc("sequence"),
            Sort.Order.asc("eventId"));

    private static final Sort EVENTS_SORT = Sort.by(
            Sort.Order.desc("occurredAt"),
            Sort.Order.asc("eventId"));

    private final AuditEntryRepository repository;
    private final ObjectMapper objectMapper;

    public AuditQueryService(AuditEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AuditPageView timelineByRequestId(UUID requestId, int page, int size) {
        return page(repository.findAll(byRequestId(requestId), PageRequest.of(page, size, TIMELINE_SORT)));
    }

    @Transactional(readOnly = true)
    public AuditPageView timelineByFolio(String folio, int page, int size) {
        return page(repository.findAll(byFolio(folio), PageRequest.of(page, size, TIMELINE_SORT)));
    }

    @Transactional(readOnly = true)
    public AuditPageView search(
            String actorId,
            String actorRole,
            String resourceType,
            List<String> eventTypes,
            UUID requestId,
            Instant from,
            Instant to,
            int page,
            int size) {
        Specification<AuditEntry> spec = Specification.where(null);
        if (actorId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actorId"), actorId));
        }
        if (actorRole != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("actorRole"), actorRole));
        }
        if (resourceType != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("resourceType"), resourceType));
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            spec = spec.and((root, q, cb) -> root.get("eventType").in(eventTypes));
        }
        if (requestId != null) {
            spec = spec.and(byRequestId(requestId));
        }
        if (from != null) {
            spec = spec.and((root, q, cb) -> cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, q, cb) -> cb.lessThanOrEqualTo(root.get("occurredAt"), to));
        }
        return page(repository.findAll(spec, PageRequest.of(page, size, EVENTS_SORT)));
    }

    private AuditPageView page(Page<AuditEntry> result) {
        List<AuditEventView> content = result.getContent().stream().map(this::toView).toList();
        return new AuditPageView(
                content,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private AuditEventView toView(AuditEntry entry) {
        return new AuditEventView(
                entry.getEventId(),
                entry.getEventType(),
                entry.getOccurredAt(),
                entry.getProducer(),
                new AuditEventView.ActorView(
                        entry.getActorId(),
                        entry.getActorName(),
                        entry.getActorRole(),
                        entry.getSourceIp()),
                entry.getResourceType(),
                entry.getResourceId(),
                entry.getRequestId(),
                entry.getFolio(),
                entry.getSequence(),
                entry.getSummary(),
                details(entry));
    }

    private JsonNode details(AuditEntry entry) {
        if (entry.getDetailsJson() != null) {
            return readTree(entry.getDetailsJson());
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.set("before", readTreeOrNull(entry.getBeforeJson()));
        node.set("after", readTreeOrNull(entry.getAfterJson()));
        return node;
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }

    private JsonNode readTreeOrNull(String json) {
        if (json == null) {
            return objectMapper.nullNode();
        }
        return readTree(json);
    }

    private static Specification<AuditEntry> byRequestId(UUID requestId) {
        return (root, q, cb) -> cb.equal(root.get("requestId"), requestId);
    }

    private static Specification<AuditEntry> byFolio(String folio) {
        return (root, q, cb) -> cb.equal(root.get("folio"), folio);
    }
}
