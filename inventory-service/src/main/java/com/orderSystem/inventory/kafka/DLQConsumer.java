package com.orderSystem.inventory.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DLQConsumer {

    @KafkaListener(topics = "order.created.DLT", groupId = "inventory-dlq-group")
    public void handleDeadLetter(Object failedMessage) {
        log.error("DEAD LETTER EVENT received — message failed after 3 retries: {}", failedMessage);
        log.error("Manual investigation required for this message");
    }
}