package io.livelattice.notifications.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public AdminClient adminClient(Environment env) {
        Map<String, Object> config = new HashMap<>();
        config.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        return AdminClient.create(config);
    }

    @Bean
    public ConsumerFactory<String, String> consumerFactory(Environment env) {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        config.put(ConsumerConfig.GROUP_ID_CONFIG, env.getProperty("spring.kafka.consumer.group-id", "livelattice-notifications"));
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, env.getProperty("spring.kafka.consumer.auto-offset-reset", "earliest"));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, env.getProperty("spring.kafka.consumer.key-deserializer", "org.apache.kafka.common.serialization.StringDeserializer"));
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, env.getProperty("spring.kafka.consumer.value-deserializer", "org.apache.kafka.common.serialization.StringDeserializer"));
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }

    @Bean
    public ProducerFactory<String, String> producerFactory(Environment env) {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, env.getProperty("spring.kafka.bootstrap-servers", "localhost:9092"));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, env.getProperty("spring.kafka.producer.key-serializer", "org.apache.kafka.common.serialization.StringSerializer"));
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, env.getProperty("spring.kafka.producer.value-serializer", "org.apache.kafka.common.serialization.StringSerializer"));
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
