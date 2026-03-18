package com.orderSystem.inventory.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private final KafkaTemplate<String, InventoryUpdatedEvent> kafkaTemplate;



    public  void publish(InventoryUpdatedEvent inventoryUpdatedEvent) {
        kafkaTemplate.send("inventory.updated",inventoryUpdatedEvent.getOrderId().toString(),inventoryUpdatedEvent);
        log.info("Published order.created event for orderId: {}", inventoryUpdatedEvent.getOrderId());

    }
}
