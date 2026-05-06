package com.orderSystem.order.model;


import com.orderSystem.order.kafka.OrderCreatedEvent;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutBoxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(nullable = false)
    private UUID orderId;

    @NotNull
    @Column(nullable = false)
    private String event_type;

    @NotNull
    @Column(nullable = false)
    private String payload;

    @NotNull
    @Column(nullable = false)
    private boolean published;



    private java.time.LocalDateTime createdAt;


}
