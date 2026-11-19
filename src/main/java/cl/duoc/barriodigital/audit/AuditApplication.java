package cl.duoc.barriodigital.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Arranca el servicio de auditoría (timeline).
 */
@SpringBootApplication
public class AuditApplication {

    /** Inicia Spring Boot con esta clase como configuración. */
    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}
