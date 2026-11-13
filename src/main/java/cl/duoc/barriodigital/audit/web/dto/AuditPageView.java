package cl.duoc.barriodigital.audit.web.dto;

import java.util.List;

/** Misma forma de página que el listado de requests (EP3-07 §2.1). */
public record AuditPageView(
        List<AuditEventView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
