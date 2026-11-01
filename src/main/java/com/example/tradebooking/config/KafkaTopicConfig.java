package com.example.tradebooking.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public record TradeEventsTopic(String name) {
    }

    @Bean
    TradeEventsTopic tradeEventsTopic(@Value("${app.kafka.topics.trade-events:trade-events}") String name) {
        return new TradeEventsTopic(name);
    }

    @Bean
    NewTopic tradeEventsNewTopic(TradeEventsTopic topic,
                                 @Value("${app.kafka.topics.partitions:3}") int partitions,
                                 @Value("${app.kafka.topics.replicas:1}") int replicas) {
        return TopicBuilder.name(topic.name()).partitions(partitions).replicas(replicas).build();
    }
}
