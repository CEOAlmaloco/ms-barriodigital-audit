package cl.duoc.barriodigital.audit.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class RequestEventsKafkaConfigTest {

    private final RequestEventMapper mapper = new RequestEventMapper(new ObjectMapper());

    @Test
    void eventoMuertoSoloDespuesDeEnviarALaDlt(CapturedOutput output) {
        KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
        when(kafka.send(any(ProducerRecord.class))).thenReturn(CompletableFuture.completedFuture(null));

        recoverer(kafka).accept(
                record("{\"eventId\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}"),
                new NonRetryableEventException("JSON invalido"));

        assertThat(output.getOut()).contains("evento-muerto topic=requests.events");
        assertThat(output.getOut()).contains("eventId=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        assertThat(output.getOut()).doesNotContain("dlt-no-disponible");
    }

    @Test
    void siLaDltFallaNoEscribeEventoMuerto(CapturedOutput output) {
        KafkaOperations<String, String> kafka = mock(KafkaOperations.class);
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker caido"));
        when(kafka.send(any(ProducerRecord.class))).thenReturn(failed);

        assertThatThrownBy(() -> recoverer(kafka).accept(
                record("{\"eventId\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}"),
                new NonRetryableEventException("JSON invalido")))
                .isInstanceOf(RuntimeException.class);

        assertThat(output.getOut()).contains("dlt-no-disponible topic=requests.events");
        assertThat(output.getOut()).contains("dlt=requests.events.audit.DLT");
        assertThat(output.getOut()).doesNotContain("evento-muerto topic=requests.events");
    }

    @Test
    void intentosCeroSiNoEsReintentable() {
        assertThat(RequestEventsKafkaConfig.attempts(new NonRetryableEventException("x"))).isZero();
        assertThat(RequestEventsKafkaConfig.attempts(new RuntimeException("agotado")))
                .isEqualTo(RequestEventsKafkaConfig.MAX_ATTEMPTS);
    }

    private ConsumerRecordRecoverer recoverer(KafkaOperations<String, String> kafka) {
        DeadLetterPublishingRecoverer dlt = new DeadLetterPublishingRecoverer(
                kafka,
                (record, ex) -> new org.apache.kafka.common.TopicPartition(
                        RequestEventsKafkaConfig.DLT_TOPIC, record.partition()));
        dlt.setFailIfSendResultIsError(true);
        return RequestEventsKafkaConfig.deadLetterRecoverer(dlt, mapper);
    }

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>(RequestEventsListener.TOPIC, 1, 2045L, "k", json);
    }
}
