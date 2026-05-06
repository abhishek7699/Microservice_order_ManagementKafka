package com.orderSystem.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderSystem.order.dto.OrderRequestDto;
import com.orderSystem.order.dto.OrderResponseDto;
import com.orderSystem.order.kafka.InventoryUpdatedEvent;
import com.orderSystem.order.kafka.OrderCreatedEvent;
import com.orderSystem.order.kafka.OrderEventProducer;
import com.orderSystem.order.model.Order;
import com.orderSystem.order.model.OutBoxEvent;
import com.orderSystem.order.repository.OrderRepository;
import com.orderSystem.order.repository.OutBoxRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    private  final OutBoxRepository outboxRepository;





    private final OrderEventProducer orderEventProducer;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;






    @Transactional
    public  ResponseEntity<OrderResponseDto> placeOrder(OrderRequestDto request,String key) throws JsonProcessingException {


        String redisKey= "Idempotency"+key;



        String cached= redisTemplate.opsForValue().get(redisKey);

        if(cached!=null){
            log.info("Duplicate request detected: {}",key);
            OrderResponseDto cachedDto = objectMapper.readValue(cached, OrderResponseDto.class);
            return ResponseEntity.ok(cachedDto);

        }



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

        OutBoxEvent outboxEvent= OutBoxEvent.builder()
                .orderId(order.getId())
                .event_type("Order Created")
                .payload(objectMapper.writeValueAsString(orderCreatedEvent))
                .published(false)
                .createdAt(LocalDateTime.now())
                .build();

        outboxRepository.save(outboxEvent);




        Order retrieved = orderRepository.findById(order.getId())
                .orElseThrow(() -> new RuntimeException("Order not found"));;



        OrderResponseDto response=OrderResponseDto.builder()
                .orderId(retrieved.getId())
                .customerId(retrieved.getCustomerId())
                .productId(retrieved.getProductId())
                .quantity(retrieved.getQuantity())
                .status(retrieved.getStatus())
                .totalPrice(retrieved.getTotalPrice())
                .build();
        redisTemplate.opsForValue().set(redisKey,objectMapper.writeValueAsString(response), Duration.ofHours(24));







                return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    public void orderStatusUpdated(InventoryUpdatedEvent inventoryUpdatedEvent){

        Order order = orderRepository.findById(inventoryUpdatedEvent.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found"));

        order.setStatus(Order.OrderStatus.valueOf(inventoryUpdatedEvent.getStatus()));

        order.setTotalPrice(inventoryUpdatedEvent.getTotalPrice());



        Order status_saved = orderRepository.save(order);






    }

    public Order viewOrder(@Valid UUID id) {

        return orderRepository.findById(id).orElseThrow(() -> new RuntimeException("Order not found"));
    }
}
