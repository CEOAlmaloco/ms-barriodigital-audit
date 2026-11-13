package cl.duoc.barriodigital.audit.web;

import cl.duoc.barriodigital.audit.domain.AuditQueryService;
import cl.duoc.barriodigital.audit.messaging.TimelineEventTypes;
import cl.duoc.barriodigital.audit.web.dto.AuditPageView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * API de solo lectura del timeline y la búsqueda (EP3-07 §5).
 * Los parámetros llegan como texto y se validan después del rol (403 antes que 400).
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    static final Set<String> ALLOWED_ROLES = Set.of(AuditCaller.ADMIN, AuditCaller.AUDITOR);

    static final String PAGE_MESSAGE = "page debe ser 0 o mayor";
    static final String SIZE_MESSAGE = "size debe estar entre 1 y 50";
    static final String REQUEST_ID_MESSAGE = "requestId no es un UUID valido";
    static final String FOLIO_MESSAGE = "folio debe tener el formato BD-AAAA-NNNNNN";
    static final String TIMELINE_KEY_MESSAGE = "Indica requestId o folio, solo uno";
    static final String ACTOR_ROLE_MESSAGE = "actorRole no es valido";
    static final String RESOURCE_TYPE_MESSAGE = "resourceType no es valido";
    static final String EVENT_TYPE_MESSAGE = "eventType no es valido";
    static final String EVENT_TYPE_REPEATED_MESSAGE = "eventType tiene valores repetidos";
    static final String FROM_TO_MESSAGE = "from y to deben ser instantes ISO-8601";
    static final String FROM_AFTER_TO_MESSAGE = "from no puede ser posterior a to";

    static final int MAX_SIZE = 50;

    private static final Set<String> ACTOR_ROLES = Set.of(
            AuditCaller.ADMIN, AuditCaller.FUNCIONARIO, AuditCaller.VECINO, AuditCaller.AUDITOR);

    private static final Set<String> RESOURCE_TYPES = Set.of(
            "request",
            "procedure-type",
            "queue",
            "exchange",
            "binding",
            "topic",
            "consumer-group");

    private static final Set<String> EVENT_TYPES;
    static {
        Set<String> types = new LinkedHashSet<>();
        types.add("request.created");
        types.add("request.status-changed");
        types.add("request.cancelled");
        types.add("request.contact-logged");
        types.add("request.assigned");
        types.add("request.rated");
        types.addAll(TimelineEventTypes.KNOWN);
        EVENT_TYPES = Set.copyOf(types);
    }

    private static final Pattern PAGE_FORMAT = Pattern.compile("\\d{1,9}");
    private static final Pattern SIZE_FORMAT = Pattern.compile("\\d{1,2}");
    private static final Pattern UUID_FORMAT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern FOLIO_FORMAT = Pattern.compile("BD-\\d{4}-\\d{6}");

    private final AuditQueryService auditQueryService;

    public AuditController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/timeline")
    public AuditPageView timeline(
            @RequestHeader(value = AuditCaller.HEADER_USER_ROLES, required = false) String roles,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "folio", required = false) String folio,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size
    ) {
        AuditCaller.requireRole(roles, ALLOWED_ROLES);
        boolean hasRequestId = requestId != null;
        boolean hasFolio = folio != null;
        if (hasRequestId == hasFolio) {
            throw badRequest(TIMELINE_KEY_MESSAGE);
        }
        int pageNumber = page(page);
        int pageSize = size(size);
        if (hasRequestId) {
            return auditQueryService.timelineByRequestId(uuid(requestId), pageNumber, pageSize);
        }
        if (!FOLIO_FORMAT.matcher(folio).matches()) {
            throw badRequest(FOLIO_MESSAGE);
        }
        return auditQueryService.timelineByFolio(folio, pageNumber, pageSize);
    }

    @GetMapping("/events")
    public AuditPageView events(
            @RequestHeader(value = AuditCaller.HEADER_USER_ROLES, required = false) String roles,
            @RequestParam(value = "actorId", required = false) String actorId,
            @RequestParam(value = "actorRole", required = false) String actorRole,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", required = false) String page,
            @RequestParam(value = "size", required = false) String size
    ) {
        AuditCaller.requireRole(roles, ALLOWED_ROLES);
        Instant fromInstant = instant(from);
        Instant toInstant = instant(to);
        if (fromInstant != null && toInstant != null && fromInstant.isAfter(toInstant)) {
            throw badRequest(FROM_AFTER_TO_MESSAGE);
        }
        return auditQueryService.search(
                actorId,
                actorRole(actorRole),
                resourceType(resourceType),
                eventTypes(eventType),
                requestId == null ? null : uuid(requestId),
                fromInstant,
                toInstant,
                page(page),
                size(size));
    }

    private static String actorRole(String value) {
        if (value == null) {
            return null;
        }
        if (!ACTOR_ROLES.contains(value)) {
            throw badRequest(ACTOR_ROLE_MESSAGE);
        }
        return value;
    }

    private static String resourceType(String value) {
        if (value == null) {
            return null;
        }
        if (!RESOURCE_TYPES.contains(value)) {
            throw badRequest(RESOURCE_TYPE_MESSAGE);
        }
        return value;
    }

    private static List<String> eventTypes(String value) {
        if (value == null) {
            return List.of();
        }
        Set<String> types = new LinkedHashSet<>();
        boolean repeated = false;
        for (String part : value.split(",", -1)) {
            if (!EVENT_TYPES.contains(part)) {
                throw badRequest(EVENT_TYPE_MESSAGE);
            }
            repeated |= !types.add(part);
        }
        if (repeated) {
            throw badRequest(EVENT_TYPE_REPEATED_MESSAGE);
        }
        return new ArrayList<>(types);
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ex) {
            throw badRequest(FROM_TO_MESSAGE);
        }
    }

    private static UUID uuid(String value) {
        if (!UUID_FORMAT.matcher(value).matches()) {
            throw badRequest(REQUEST_ID_MESSAGE);
        }
        return UUID.fromString(value.toLowerCase(Locale.ROOT));
    }

    private static int page(String value) {
        if (value == null) {
            return 0;
        }
        if (!PAGE_FORMAT.matcher(value).matches()) {
            throw badRequest(PAGE_MESSAGE);
        }
        return Integer.parseInt(value);
    }

    private static int size(String value) {
        if (value == null) {
            return 20;
        }
        if (!SIZE_FORMAT.matcher(value).matches()) {
            throw badRequest(SIZE_MESSAGE);
        }
        int parsed = Integer.parseInt(value);
        if (parsed < 1 || parsed > MAX_SIZE) {
            throw badRequest(SIZE_MESSAGE);
        }
        return parsed;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
