package com.orderSystem.order.service;

import com.orderSystem.order.dto.OrderRequestDto;
import com.orderSystem.order.dto.OrderResponseDto;
import com.orderSystem.order.kafka.InventoryEventConsumer;
import com.orderSystem.order.kafka.InventoryUpdatedEvent;
import com.orderSystem.order.kafka.OrderCreatedEvent;
import com.orderSystem.order.kafka.OrderEventProducer;
import com.orderSystem.order.model.Order;
import com.orderSystem.order.repository.OrderRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

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

         orderRepository.save(order);

        OrderCreatedEvent orderCreatedEvent= new OrderCreatedEvent();
        orderCreatedEvent.setOrderId(order.getId());
        orderCreatedEvent.setProductId(order.getProductId());
        orderCreatedEvent.setQuantity(order.getQuantity());

        orderEventProducer.publishOrderCreated(orderCreatedEvent);


        Order retrieved = orderRepository.findById(order.getId())
                .orElseThrow(() -> new RuntimeException("Order not found"));;


        return OrderResponseDto.builder()
                .orderId(retrieved.getId())
                .customerId(retrieved.getCustomerId())
                .productId(retrieved.getProductId())
                .quantity(retrieved.getQuantity())
                .status(retrieved.getStatus())
                .totalPrice(retrieved.getTotalPrice())
                .build();
    }

    public void orderStatusUpdated(InventoryUpdatedEvent inventoryUpdatedEvent){

        Order order = orderRepository.findById(inventoryUpdatedEvent.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(Order.OrderStatus.valueOf(inventoryUpdatedEvent.getStatus()));

        order.setTotalPrice(inventoryUpdatedEvent.getTotalPrice());



        Order saved = orderRepository.save(order);







    }

    public Order viewOrder(@Valid UUID id) {

        return orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
    }
}
