package com.orderSystem.inventory.service;

import com.orderSystem.inventory.dto.ProductResponseDto;
import com.orderSystem.inventory.kafka.InventoryUpdatedEvent;
import com.orderSystem.inventory.kafka.OrderCreatedEvent;
import com.orderSystem.inventory.model.Product;
import com.orderSystem.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductResponseDto> getAllProducts() {
        return productRepository.findAll().stream()
                .map(this::toDto)
                .toList();
    }

    public Optional<ProductResponseDto> getProductById(UUID id) {
        return productRepository.findById(id).map(this::toDto);
    }

    private ProductResponseDto toDto(Product product) {
        return ProductResponseDto.builder()
                .id(product.getId())
                .name(product.getName())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .build();
    }

    public InventoryUpdatedEvent processOrder(OrderCreatedEvent event){
        int orderStock= event.getQuantity();

        Product product=productRepository.findById(UUID.fromString(event.getProductId()))
                .orElseThrow(() -> new RuntimeException("Product not found"));

        InventoryUpdatedEvent inventoryUpdatedEvent= new InventoryUpdatedEvent();


        int inventoryStock= product.getStockQuantity();

        inventoryUpdatedEvent.setOrderId(event.getOrderId());

        if(orderStock<=inventoryStock){

            int newInventoryStock= inventoryStock-orderStock;

            product.setStockQuantity(newInventoryStock);

            productRepository.save(product);


             inventoryUpdatedEvent.setStatus("CONFIRMED");
             return inventoryUpdatedEvent;

        }
        else{
            inventoryUpdatedEvent.setStatus("FAILED");
            return inventoryUpdatedEvent;

        }

    }

}
