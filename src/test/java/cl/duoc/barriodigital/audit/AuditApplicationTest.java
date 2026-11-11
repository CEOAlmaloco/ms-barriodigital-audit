package cl.duoc.barriodigital.audit;

import cl.duoc.barriodigital.audit.domain.AuditTrailService;
import cl.duoc.barriodigital.audit.messaging.AuditTimelineListener;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Arranque del contexto con H2 (sin Oracle ni Kafka reales).
 */
@SpringBootTest
class AuditApplicationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private AuditTrailService auditTrailService;

    @Autowired
    private AuditTimelineListener auditTimelineListener;

    @Test
    void elContextoArranca() {
        assertThat(environment.getProperty("server.port")).isEqualTo("8087");
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("ms-barriodigital-audit");
        assertThat(auditTrailService).isNotNull();
        assertThat(auditTimelineListener).isNotNull();
    }
}
