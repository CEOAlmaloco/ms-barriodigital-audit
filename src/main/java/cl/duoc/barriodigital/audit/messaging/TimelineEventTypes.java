package cl.duoc.barriodigital.audit.messaging;

import java.util.Set;

/** eventType válidos de audit.timeline (EP3-01 §7). */
public final class TimelineEventTypes {

    public static final Set<String> KNOWN = Set.of(
            "procedure-type.created",
            "procedure-type.updated",
            "queue.created",
            "queue.deleted",
            "queue.purged",
            "exchange.created",
            "exchange.deleted",
            "binding.created",
            "binding.deleted",
            "topic.created",
            "topic.updated",
            "topic.deleted",
            "topic.dlt-replayed",
            "consumer-group.offsets-reset"
    );

    private TimelineEventTypes() {
    }

    public static boolean isKnown(String eventType) {
        return eventType != null && KNOWN.contains(eventType);
    }
}
