package com.orderSystem.order.kafka;

import com.orderSystem.order.dto.OrderResponseDto;
import com.orderSystem.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {


    @Autowired
    OrderService orderService;


    @KafkaListener(topics = "inventory.updated", groupId = "order-group")
    public void orderServiceUpdate(InventoryUpdatedEvent inventoryUpdatedEvent){

        orderService.orderStatusUpdated(inventoryUpdatedEvent);
        log.info("Published order.created event for orderId: {}", inventoryUpdatedEvent.getOrderId());





    }
}
