package com.gritmoments.backend.common.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 설정 (세션 04: 비동기 처리)
 *
 * Exchange, Queue, Binding을 선언하고 메시지 변환기를 설정합니다.
 * - Direct Exchange: 라우팅 키 기반으로 정확한 큐에 메시지 전달
 * - Dead Letter Queue: 처리 실패 메시지를 별도 큐로 이동
 * - Jackson 메시지 변환: Java 객체 ↔ JSON 자동 변환
 */
@Configuration
public class RabbitMQConfig {

    // Exchange
    public static final String ORDER_EXCHANGE = "order.exchange";

    // Queue
    public static final String NOTIFICATION_QUEUE = "order.notification.queue";
    public static final String NOTIFICATION_DLQ = "order.notification.dlq";

    // Routing Key
    public static final String ORDER_CREATED_KEY = "order.created";

    // --- Exchange 선언 ---
    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    // --- Queue 선언 ---
    @Bean
    public Queue notificationQueue() {
        return QueueBuilder.durable(NOTIFICATION_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", NOTIFICATION_DLQ)
                .build();
    }

    @Bean
    public Queue notificationDlq() {
        return QueueBuilder.durable(NOTIFICATION_DLQ).build();
    }

    // --- Binding 선언 ---
    @Bean
    public Binding notificationBinding(Queue notificationQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(notificationQueue)
                .to(orderExchange)
                .with(ORDER_CREATED_KEY);
    }

    // --- 메시지 변환기 ---
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                        MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
