package com.orderSystem.order.service;

import com.orderSystem.order.dto.OrderRequestDto;
import com.orderSystem.order.dto.OrderResponseDto;
import com.orderSystem.order.kafka.InventoryEventConsumer;
import com.orderSystem.order.kafka.InventoryUpdatedEvent;
import com.orderSystem.order.kafka.OrderCreatedEvent;
import com.orderSystem.order.kafka.OrderEventProducer;
import com.orderSystem.order.model.Order;
import com.orderSystem.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;





    private final OrderEventProducer orderEventProducer;





    public OrderResponseDto placeOrder(OrderRequestDto request) {
        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .build();

        Order saved = orderRepository.save(order);

        OrderCreatedEvent orderCreatedEvent= new OrderCreatedEvent();
        orderCreatedEvent.setOrderId(order.getId());
        orderCreatedEvent.setProductId(order.getProductId());
        orderCreatedEvent.setQuantity(order.getQuantity());

        orderEventProducer.publishOrderCreated(orderCreatedEvent);


        return OrderResponseDto.builder()
                .orderId(saved.getId())
                .customerId(saved.getCustomerId())
                .productId(saved.getProductId())
                .quantity(saved.getQuantity())
                .status(saved.getStatus())
                .totalPrice(saved.getTotalPrice())
                .build();
    }

    public void orderStatusUpdated(InventoryUpdatedEvent inventoryUpdatedEvent){

        Order order = orderRepository.findById(inventoryUpdatedEvent.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(Order.OrderStatus.valueOf(inventoryUpdatedEvent.getStatus()));

        order.setTotalPrice();



        Order saved = orderRepository.save(order);

        OrderResponseDto.builder()
                .orderId(saved.getId())
                .customerId(saved.getCustomerId())
                .productId(saved.getProductId())
                .quantity(saved.getQuantity())
                .status(saved.getStatus())
                .totalPrice(saved.getTotalPrice())
                .build();





    }
}
