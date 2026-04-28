package com.example.notification_service.domains;

import com.example.notification_service.domains.models.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    public void sendOrderCreatedNotification(OrderCreatedEvent event) {
        log.info("Sending notification for OrderCreatedEvent - orderNumber: {}, customerEmail: {}",
                event.orderNumber(),
                event.customer().email()
        );

        // Simulate email sending
        log.info("Email sent successfully to {}", event.customer().email());
    }
}