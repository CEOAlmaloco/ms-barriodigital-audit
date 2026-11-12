package cl.duoc.barriodigital.audit.messaging;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.regex.Pattern;

/**
 * Reconoce un duplicado solo por la violación de uk_audit_entries_event_id.
 * Cualquier otra violación, aunque sea de unicidad, se propaga a los reintentos.
 */
final class DuplicateEvents {

    /**
     * Nombre exacto: Oracle lo informa como ESQUEMA.UK_AUDIT_ENTRIES_EVENT_ID.
     * H2 a veces nombra el índice UK_AUDIT_ENTRIES_EVENT_ID_INDEX_N.
     */
    private static final Pattern CONSTRAINT = Pattern.compile(
            "(?<![A-Z0-9_$#])UK_AUDIT_ENTRIES_EVENT_ID(?:_INDEX_\\d+)?(?![A-Z0-9_$#])",
            Pattern.CASE_INSENSITIVE);

    private DuplicateEvents() {
    }

    static boolean isDuplicate(DataIntegrityViolationException ex) {
        for (Throwable current = ex; current != null; current = next(current)) {
            if (current instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return CONSTRAINT.matcher(violation.getConstraintName()).find();
            }
        }
        for (Throwable current = ex; current != null; current = next(current)) {
            if (current.getMessage() != null && CONSTRAINT.matcher(current.getMessage()).find()) {
                return true;
            }
        }
        return false;
    }

    private static Throwable next(Throwable current) {
        Throwable cause = current.getCause();
        return cause == current ? null : cause;
    }
}
