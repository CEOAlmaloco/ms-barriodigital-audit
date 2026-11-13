package cl.duoc.barriodigital.audit.messaging;

import java.util.Set;

/** eventType válidos de requests.events (EP3-01 §6). */
public final class RequestEventTypes {

    public static final Set<String> KNOWN = Set.of(
            "request.created",
            "request.status-changed",
            "request.cancelled",
            "request.contact-logged",
            "request.assigned",
            "request.rated"
    );

    private RequestEventTypes() {
    }

    public static boolean isKnown(String eventType) {
        return eventType != null && KNOWN.contains(eventType);
    }
}
