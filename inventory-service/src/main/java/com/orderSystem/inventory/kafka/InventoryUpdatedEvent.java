package com.orderSystem.inventory.kafka;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class InventoryUpdatedEvent {
    private UUID orderId;
    private String status;
    private double totalPrice;

}
