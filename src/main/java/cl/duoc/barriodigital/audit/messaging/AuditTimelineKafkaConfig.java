package cl.duoc.barriodigital.audit.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;

/**
 * Errores del consumidor de audit.timeline: reintentos 1/2/4 s y DLT (EP3-02 §8).
 */
@Configuration
public class AuditTimelineKafkaConfig {

    public static final String DLT_TOPIC = "audit.timeline.audit.DLT";

    private static final Logger log = LoggerFactory.getLogger(AuditTimelineKafkaConfig.class);

    @Bean
    CommonErrorHandler auditTimelineErrorHandler(KafkaOperations<String, String> kafkaOperations) {
        DeadLetterPublishingRecoverer dltRecoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(DLT_TOPIC, record.partition()));

        ConsumerRecordRecoverer recoverer = (record, ex) -> {
            logDeadLetter(record, ex);
            dltRecoverer.accept(record, ex);
        };

        BackOff backOff = new ExponentialBackOffWithMaxRetries(3);
        ((ExponentialBackOffWithMaxRetries) backOff).setInitialInterval(1_000L);
        ((ExponentialBackOffWithMaxRetries) backOff).setMultiplier(2.0);
        ((ExponentialBackOffWithMaxRetries) backOff).setMaxInterval(4_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        return handler;
    }

    private static void logDeadLetter(ConsumerRecord<?, ?> record, Exception ex) {
        String eventId = "desconocido";
        Object value = record.value();
        if (value instanceof String json && json.contains("\"eventId\"")) {
            int start = json.indexOf("\"eventId\"");
            int colon = json.indexOf(':', start);
            int quote1 = json.indexOf('"', colon + 1);
            int quote2 = json.indexOf('"', quote1 + 1);
            if (quote1 > 0 && quote2 > quote1) {
                eventId = json.substring(quote1 + 1, quote2);
            }
        }
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String motivo = root.getMessage() != null ? root.getMessage() : root.getClass().getSimpleName();
        // intentos: 0 si no recuperable; 4 si agotó reintentos (1 + 3)
        int intentos = ex instanceof NonRetryableEventException
                || (ex.getCause() instanceof NonRetryableEventException)
                ? 0
                : 4;
        log.error(
                "evento-muerto topic={} partition={} offset={} eventId={} motivo=\"{}\" intentos={}",
                record.topic(),
                record.partition(),
                record.offset(),
                eventId,
                motivo,
                intentos);
    }
}
