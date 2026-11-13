package cl.duoc.barriodigital.audit.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DeserializationException;

/**
 * Errores del consumidor de requests.events: reintentos 1/2/4 s y DLT (misma configuración que EP3-36).
 *
 * <p>El factory es propio para no mezclar esta DLT con la de audit.timeline.
 * El offset se confirma en {@link ContainerProperties.AckMode#RECORD}, después del commit en Oracle.
 * Si la DLT no confirma, el recoverer relanza: el offset no se confirma.
 */
@Configuration
public class RequestEventsKafkaConfig {

    public static final String DLT_TOPIC = "requests.events.audit.DLT";

    static final int MAX_RETRIES = 3;
    static final int MAX_ATTEMPTS = MAX_RETRIES + 1;
    private static final int MAX_REASON_LENGTH = 300;

    private static final Logger log = LoggerFactory.getLogger(RequestEventsKafkaConfig.class);

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> requestEventsKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaOperations<String, String> kafkaOperations,
            RequestEventMapper mapper,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setAutoStartup(autoStartup);
        factory.setConcurrency(3);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errorHandler(kafkaOperations, mapper));
        return factory;
    }

    private static CommonErrorHandler errorHandler(
            KafkaOperations<String, String> kafkaOperations,
            RequestEventMapper mapper) {
        DeadLetterPublishingRecoverer dltPublisher = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(DLT_TOPIC, record.partition()));
        dltPublisher.setFailIfSendResultIsError(true);

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(1_000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(deadLetterRecoverer(dltPublisher, mapper), backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        return handler;
    }

    /** Recoverer expuesto para tests: DLT primero; {@code evento-muerto} solo si confirma. */
    static ConsumerRecordRecoverer deadLetterRecoverer(
            DeadLetterPublishingRecoverer dltPublisher,
            RequestEventMapper mapper) {
        return (record, ex) -> {
            String eventId = eventId(record, mapper);
            try {
                dltPublisher.accept(record, ex);
            } catch (RuntimeException dltFailure) {
                log.error(
                        "dlt-no-disponible topic={} partition={} offset={} eventId={} dlt={} motivo=\"{}\"",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        eventId,
                        DLT_TOPIC,
                        clean(String.valueOf(dltFailure.getMessage())));
                throw dltFailure;
            }
            log.error(
                    "evento-muerto topic={} partition={} offset={} eventId={} motivo=\"{}\" intentos={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    eventId,
                    reason(ex),
                    attempts(ex));
        };
    }

    /** Veces que se ejecutó el procesamiento: 0 en un rechazo directo. */
    static int attempts(Throwable ex) {
        return find(ex, NonRetryableEventException.class) != null || find(ex, DeserializationException.class) != null
                ? 0 : MAX_ATTEMPTS;
    }

    static String reason(Throwable ex) {
        Throwable source = find(ex, NonRetryableEventException.class);
        if (source == null) {
            source = ex;
            while (source.getCause() != null && source.getCause() != source) {
                source = source.getCause();
            }
        }
        return clean(source.getMessage() != null ? source.getMessage() : source.getClass().getSimpleName());
    }

    private static String clean(String message) {
        String clean = message.replace('"', '\'').replaceAll("\\s+", " ").trim();
        return clean.length() > MAX_REASON_LENGTH ? clean.substring(0, MAX_REASON_LENGTH) : clean;
    }

    private static String eventId(ConsumerRecord<?, ?> record, RequestEventMapper mapper) {
        String eventId = record.value() instanceof String json ? mapper.peekEventId(json) : null;
        return eventId == null ? "desconocido" : eventId;
    }

    private static <T extends Throwable> T find(Throwable ex, Class<T> type) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }
}
