package cl.duoc.barriodigital.audit.web;

import cl.duoc.barriodigital.audit.domain.AuditEntry;
import cl.duoc.barriodigital.audit.domain.AuditEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerTest {

    private static final String TIMELINE = "/api/audit/timeline";
    private static final String EVENTS = "/api/audit/events";
    private static final UUID REQUEST_ID = UUID.fromString("7b6e1d44-2a18-4c90-9f01-88aa11bb22cc");
    private static final String FOLIO = "BD-2026-000123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AuditEntryRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Admin", "Auditor", "ROLE_Admin", "auditor"})
    void adminYAuditorVenAmbosEndpoints(String role) throws Exception {
        mvc.perform(as(get(TIMELINE).param("requestId", REQUEST_ID.toString()), role))
                .andExpect(status().isOk());
        mvc.perform(as(get(EVENTS), role)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Vecino", "Funcionario", "Admin,Auditor", "Otro", " "})
    void rolNoPermitidoOAmbiguoResponde403(String role) throws Exception {
        expectError(mvc.perform(as(get(TIMELINE).param("requestId", REQUEST_ID.toString()), role)),
                403, AuditCaller.FORBIDDEN_MESSAGE, TIMELINE);
        expectError(mvc.perform(as(get(EVENTS), role)),
                403, AuditCaller.FORBIDDEN_MESSAGE, EVENTS);
    }

    @Test
    void elRolSeRevisaAntesQueLosParametros() throws Exception {
        expectError(mvc.perform(as(get(EVENTS).param("actorRole", "xyz"), "Vecino")),
                403, AuditCaller.FORBIDDEN_MESSAGE, EVENTS);
    }

    @Test
    void timelineOrdenaPorOccurredAtYSequence() throws Exception {
        seedRequest("a1a1a1a1-a1a1-4a1a-a1a1-a1a1a1a1a1a1", "request.created",
                Instant.parse("2026-11-10T13:00:00Z"), 10L, "Funcionario");
        seedRequest("b2b2b2b2-b2b2-4b2b-b2b2-b2b2b2b2b2b2", "request.status-changed",
                Instant.parse("2026-11-10T13:00:00Z"), 12L, "Admin");
        seedRequest("c3c3c3c3-c3c3-4c3c-c3c3-c3c3c3c3c3c3", "request.assigned",
                Instant.parse("2026-11-10T12:00:00Z"), 5L, "Admin");

        mvc.perform(as(get(TIMELINE).param("requestId", REQUEST_ID.toString()), "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].eventId").value("c3c3c3c3-c3c3-4c3c-c3c3-c3c3c3c3c3c3"))
                .andExpect(jsonPath("$.content[1].eventId").value("a1a1a1a1-a1a1-4a1a-a1a1-a1a1a1a1a1a1"))
                .andExpect(jsonPath("$.content[2].eventId").value("b2b2b2b2-b2b2-4b2b-b2b2-b2b2b2b2b2b2"))
                .andExpect(jsonPath("$.content[2].summary").value("Cambio de estado"));
    }

    @Test
    void timelinePorFolioYSinResultadosEsListaVacia() throws Exception {
        mvc.perform(as(get(TIMELINE).param("folio", FOLIO), "Auditor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void timelineConAmbosONingunoResponde400() throws Exception {
        expectError(mvc.perform(as(get(TIMELINE), "Admin")),
                400, AuditController.TIMELINE_KEY_MESSAGE, TIMELINE);
        expectError(mvc.perform(as(get(TIMELINE)
                        .param("requestId", REQUEST_ID.toString())
                        .param("folio", FOLIO), "Admin")),
                400, AuditController.TIMELINE_KEY_MESSAGE, TIMELINE);
    }

    @Test
    void busquedaFiltraPorActorRoleAdmin() throws Exception {
        seedRequest("a1a1a1a1-a1a1-4a1a-a1a1-a1a1a1a1a1a1", "request.status-changed",
                Instant.parse("2026-11-10T15:00:00Z"), 20L, "Admin");
        seedRequest("b2b2b2b2-b2b2-4b2b-b2b2-b2b2b2b2b2b2", "request.assigned",
                Instant.parse("2026-11-10T16:00:00Z"), 21L, "Admin");
        seedRequest("c3c3c3c3-c3c3-4c3c-c3c3-c3c3c3c3c3c3", "request.contact-logged",
                Instant.parse("2026-11-10T17:00:00Z"), 22L, "Funcionario");
        seedTimeline("d4d4d4d4-d4d4-4d4d-d4d4-d4d4d4d4d4d4", Instant.parse("2026-11-10T18:00:00Z"), "Admin");

        mvc.perform(as(get(EVENTS)
                        .param("actorRole", "Admin")
                        .param("eventType", "request.status-changed,request.contact-logged"), "Auditor"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].eventType").value("request.status-changed"))
                .andExpect(jsonPath("$.content[0].actor.role").value("Admin"));
    }

    @Test
    void busquedaOrdenaPorOccurredAtDescendente() throws Exception {
        seedRequest("a1a1a1a1-a1a1-4a1a-a1a1-a1a1a1a1a1a1", "request.created",
                Instant.parse("2026-11-10T13:00:00Z"), 1L, "Vecino");
        seedRequest("b2b2b2b2-b2b2-4b2b-b2b2-b2b2b2b2b2b2", "request.rated",
                Instant.parse("2026-11-12T18:00:00Z"), 2L, "Vecino");

        mvc.perform(as(get(EVENTS).param("resourceType", "request"), "Admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].eventType").value("request.rated"))
                .andExpect(jsonPath("$.content[1].eventType").value("request.created"));
    }

    @Test
    void actorRoleInvalidoResponde400() throws Exception {
        expectError(mvc.perform(as(get(EVENTS).param("actorRole", "SuperAdmin"), "Admin")),
                400, AuditController.ACTOR_ROLE_MESSAGE, EVENTS);
    }

    private void seedRequest(
            String eventId,
            String eventType,
            Instant occurredAt,
            Long sequence,
            String actorRole) {
        AuditEntry entry = new AuditEntry();
        entry.setEventId(UUID.fromString(eventId));
        entry.setEventType(eventType);
        entry.setOccurredAt(occurredAt);
        entry.setProducer("requests");
        entry.setActorId("actor-" + actorRole);
        entry.setActorName(actorRole + " Test");
        entry.setActorRole(actorRole);
        entry.setResourceType("request");
        entry.setResourceId(REQUEST_ID.toString());
        entry.setRequestId(REQUEST_ID);
        entry.setFolio(FOLIO);
        entry.setSequence(sequence);
        entry.setSummary(summaryFor(eventType));
        entry.setDetailsJson("{\"requestId\":\"" + REQUEST_ID + "\",\"folio\":\"" + FOLIO + "\"}");
        repository.save(entry);
    }

    private void seedTimeline(String eventId, Instant occurredAt, String actorRole) {
        AuditEntry entry = new AuditEntry();
        entry.setEventId(UUID.fromString(eventId));
        entry.setEventType("procedure-type.updated");
        entry.setOccurredAt(occurredAt);
        entry.setProducer("catalog");
        entry.setActorId("actor-" + actorRole);
        entry.setActorName(actorRole + " Test");
        entry.setActorRole(actorRole);
        entry.setSourceIp("203.0.113.25");
        entry.setResourceType("procedure-type");
        entry.setResourceId("bache");
        entry.setAction("updated");
        entry.setBeforeJson("{\"dailyQuota\":20}");
        entry.setAfterJson("{\"dailyQuota\":15}");
        entry.setResult("SUCCESS");
        entry.setSummary("Edicion del tipo de tramite bache");
        repository.save(entry);
    }

    private static String summaryFor(String eventType) {
        return switch (eventType) {
            case "request.created" -> "Alta del tramite";
            case "request.status-changed" -> "Cambio de estado";
            case "request.assigned" -> "Asignacion";
            case "request.contact-logged" -> "Gestion";
            case "request.rated" -> "Calificacion";
            default -> eventType;
        };
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String role) {
        return request.header(AuditCaller.HEADER_USER_ROLES, role);
    }

    private static void expectError(ResultActions result, int status, String message, String path) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.path").value(path));
    }
}
