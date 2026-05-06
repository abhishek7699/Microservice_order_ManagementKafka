package com.orderSystem.order.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.orderSystem.order.dto.OrderRequestDto;
import com.orderSystem.order.dto.OrderResponseDto;
import com.orderSystem.order.model.Order;
import com.orderSystem.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

import static org.yaml.snakeyaml.tokens.Token.ID.Key;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;


    @GetMapping("/{id}")
    public Order ViewOrder(@Valid @PathVariable UUID id) {

        return orderService.viewOrder(id);
    }

    @PostMapping
    public ResponseEntity<OrderResponseDto> placeOrder(@RequestHeader("Idempotency-key") String key,@Valid @RequestBody OrderRequestDto request) throws JsonProcessingException {
        return  orderService.placeOrder(request,key);

    }
}
