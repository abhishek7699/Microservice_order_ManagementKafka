package com.orderSystem.inventory.kafka;


import com.orderSystem.inventory.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final InventoryEventProducer inventoryEventProducer;

    @Autowired
    ProductService productService;

    @KafkaListener(topics = "order.created", groupId = "inventory-group")
    public void handleOrderCreated(OrderCreatedEvent event){



        log.info("Received order.created event for orderId: {}", event.getOrderId());

        InventoryUpdatedEvent inventoryUpdatedEvent=productService.processOrder(event);

        inventoryEventProducer.publish(inventoryUpdatedEvent);


    }



}
