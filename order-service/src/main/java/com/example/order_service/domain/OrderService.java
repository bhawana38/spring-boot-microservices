package com.example.order_service.domain;

import com.example.order_service.domain.models.*;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final List<String> DELIVERY_ALLOWED_COUNTRIES =
            List.of("INDIA", "USA", "GERMANY", "UK");

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;
    private final OrderEventPublisher orderEventPublisher;

    OrderService(OrderRepository orderRepository,
                 OrderValidator orderValidator,
                 OrderEventPublisher orderEventPublisher) {
        this.orderRepository = orderRepository;
        this.orderValidator = orderValidator;
        this.orderEventPublisher = orderEventPublisher;
    }

    public CreateOrderResponse createOrder(String userName, CreateOrderRequest request) {
        orderValidator.validate(request);

        OrderEntity newOrder = OrderMapper.convertToEntity(request);
        newOrder.setUserName(userName);

        OrderEntity savedOrder = this.orderRepository.save(newOrder);
        log.info("Created Order with orderNumber={}", savedOrder.getOrderNumber());

        // Build event
        OrderCreatedEvent event =
                OrderEventMapper.buildOrderCreatedEvent(savedOrder);

        // Publish event to RabbitMQ
        orderEventPublisher.publishOrderCreatedEvent(event);

        return new CreateOrderResponse(savedOrder.getOrderNumber());
    }

    public List<OrderSummary> findOrders(String userName) {
        return orderRepository.findByUserName(userName);
    }

    public Optional<OrderDTO> findUserOrder(String userName, String orderNumber) {
        return orderRepository
                .findByUserNameAndOrderNumber(userName, orderNumber)
                .map(OrderMapper::convertToDTO);
    }

    public void processNewOrders() {
        List<OrderEntity> orders = orderRepository.findByStatus(OrderStatus.NEW);
        log.info("Found {} new orders to process", orders.size());

        for (OrderEntity order : orders) {
            this.process(order);
        }
    }

    private void process(OrderEntity order) {
        try {
            if (canBeDelivered(order)) {
                log.info("OrderNumber: {} can be delivered", order.getOrderNumber());
                orderRepository.updateOrderStatus(
                        order.getOrderNumber(), OrderStatus.DELIVERED);

            } else {
                log.info("OrderNumber: {} cannot be delivered", order.getOrderNumber());
                orderRepository.updateOrderStatus(
                        order.getOrderNumber(), OrderStatus.CANCELLED);
            }
        } catch (RuntimeException e) {
            log.error("Failed to process Order with orderNumber: {}",
                    order.getOrderNumber(), e);

            orderRepository.updateOrderStatus(
                    order.getOrderNumber(), OrderStatus.ERROR);
        }
    }

    private boolean canBeDelivered(OrderEntity order) {
        return DELIVERY_ALLOWED_COUNTRIES.contains(
                order.getDeliveryAddress().country().toUpperCase()
        );
    }
}