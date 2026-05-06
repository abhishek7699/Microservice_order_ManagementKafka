package com.orderSystem.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequestDto {

    @NotBlank
    private String customerId;

    @NotBlank
    private String productId;

    @NotNull
    @Min(1)
    private int quantity;
}
