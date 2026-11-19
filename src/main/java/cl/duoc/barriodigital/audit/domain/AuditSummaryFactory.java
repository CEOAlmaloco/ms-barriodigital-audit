package cl.duoc.barriodigital.audit.domain;

/** Arma el summary en español para mostrar en pantalla (EP3-07 §5.1). */
public final class AuditSummaryFactory {

    private AuditSummaryFactory() {
    }

    public static String forRequest(String eventType, String folio, String fromStatus, String toStatus) {
        return switch (eventType) {
            case "request.created" -> "Alta del tramite " + folio;
            case "request.status-changed" -> "Cambio de estado de " + fromStatus + " a " + toStatus;
            case "request.cancelled" -> "Cancelacion del tramite " + folio;
            case "request.contact-logged" -> "Gestion de contacto del tramite " + folio;
            case "request.assigned" -> "Asignacion del tramite " + folio;
            case "request.rated" -> "Calificacion del tramite " + folio;
            case "request.citizen-comment" -> "Comentario del vecino en el tramite " + folio;
            default -> eventType + " " + folio;
        };
    }

    public static String forTimeline(String resourceType, String resourceId, String action) {
        return switch (resourceType + "." + action) {
            case "procedure-type.created" -> "Alta del tipo de tramite " + resourceId;
            case "procedure-type.updated" -> "Edicion del tipo de tramite " + resourceId;
            case "queue.created" -> "Alta de la cola " + resourceId;
            case "queue.deleted" -> "Eliminacion de la cola " + resourceId;
            case "queue.purged" -> "Purga de la cola " + resourceId;
            case "exchange.created" -> "Alta del exchange " + resourceId;
            case "exchange.deleted" -> "Eliminacion del exchange " + resourceId;
            case "binding.created" -> "Alta del binding " + resourceId;
            case "binding.deleted" -> "Eliminacion del binding " + resourceId;
            case "topic.created" -> "Alta del topico " + resourceId;
            case "topic.updated" -> "Actualizacion del topico " + resourceId;
            case "topic.deleted" -> "Eliminacion del topico " + resourceId;
            case "topic.dlt-replayed" -> "Reproceso DLT del topico " + resourceId;
            case "consumer-group.offsets-reset" -> "Reset de offsets del grupo " + resourceId;
            default -> resourceType + " " + action + " " + resourceId;
        };
    }
}
