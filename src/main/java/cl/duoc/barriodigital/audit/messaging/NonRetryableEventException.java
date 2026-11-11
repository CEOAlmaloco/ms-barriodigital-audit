package cl.duoc.barriodigital.audit.messaging;

/**
 * Error no recuperable: malformado o incompatible → DLT sin reintentos (EP3-02 §8.1).
 */
public class NonRetryableEventException extends RuntimeException {

    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
