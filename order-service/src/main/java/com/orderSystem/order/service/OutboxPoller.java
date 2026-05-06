package com.orderSystem.order.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderSystem.order.kafka.OrderCreatedEvent;
import com.orderSystem.order.kafka.OrderEventProducer;
import com.orderSystem.order.model.OutBoxEvent;
import com.orderSystem.order.repository.OutBoxRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {

    private final OutBoxRepository outboxRepository;
    private final OrderEventProducer orderEventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void pollOutBox() throws JsonProcessingException {

        List<OutBoxEvent> unpublished = outboxRepository.findByPublishedFalse();

        for(OutBoxEvent entry: unpublished){

                try {
                    OrderCreatedEvent event = objectMapper.readValue(entry.getPayload(), OrderCreatedEvent.class);

                    orderEventProducer.publishOrderCreated(event);

                    entry.setPublished(true);
                    outboxRepository.save(entry);
                    log.info("Outbox event published for orderId: {}", entry.getOrderId());
                }
                catch(Exception e){
                    log.error("Failed to publish outbox event for orderId: {}", entry.getOrderId(), e);
                }



        }





    }




}
