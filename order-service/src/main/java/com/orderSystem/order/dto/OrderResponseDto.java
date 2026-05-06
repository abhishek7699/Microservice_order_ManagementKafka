package com.orderSystem.order.dto;

import com.orderSystem.order.model.Order.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDto {
    private UUID orderId;
    private String customerId;
    private String productId;
    private int quantity;
    private OrderStatus status;
    private double totalPrice;
}
