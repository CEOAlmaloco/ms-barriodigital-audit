package cl.duoc.barriodigital.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistencia de filas de auditoría. */
public interface AuditEntryRepository extends JpaRepository<AuditEntry, Long> {

    Optional<AuditEntry> findByEventId(UUID eventId);

    boolean existsByEventId(UUID eventId);
}
