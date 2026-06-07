package cm.imf.pipeline.kafka;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Apache Kafka pour Spring Boot.
 *
 * Producteurs : CollecteKafkaProducer, AlerteKafkaProducer
 * Consommateurs : ScoringResultConsumer (reçoit les scores du service ML Python)
 *
 * Sérialisation : Apache Avro via Confluent Schema Registry
 * Topics : définis dans KafkaTopics.java
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${kafka.bootstrap-servers:localhost:9093}")
    private String bootstrapServers;

    @Value("${kafka.schema-registry-url:http://localhost:8081}")
    private String schemaRegistryUrl;

    @Value("${kafka.consumer.group-id:imf-backend-spring}")
    private String consumerGroupId;

    // ─── Producer ──────────────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,         bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,      StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,    KafkaAvroSerializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        // Durabilité
        props.put(ProducerConfig.ACKS_CONFIG,                      "all");
        props.put(ProducerConfig.RETRIES_CONFIG,                   3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,        true);
        props.put(ProducerConfig.LINGER_MS_CONFIG,                 10);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG,                16384);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG,          "snappy");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ─── Consumer ──────────────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,         bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                  consumerGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,    StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,  KafkaAvroDeserializer.class);
        props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        props.put("specific.avro.reader",                          true);
        // Fiabilité
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,         "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,        false);
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,      300000);
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,        45000);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, Object>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.MANUAL_IMMEDIATE
        );
        // Retry : 3 tentatives espacées de 2s avant DLQ
        factory.setCommonErrorHandler(
            new DefaultErrorHandler(new FixedBackOff(2000L, 3L))
        );
        return factory;
    }
}
