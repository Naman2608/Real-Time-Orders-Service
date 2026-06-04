package com.realtime.orders.service;

import com.realtime.orders.dto.OrderRequest;
import com.realtime.orders.model.Order;
import com.realtime.orders.repository.OrderRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderService {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "shipped", "delivered");

    // Valid transitions: pending → shipped → delivered (no skipping, no going back)
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "pending",   Set.of("shipped"),
            "shipped",   Set.of("delivered"),
            "delivered", Set.of()
    );

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public Order getOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));
    }

    public Order createOrder(OrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setProductName(request.getProductName());
        if (request.getStatus() != null) {
            order.setStatus(request.getStatus());
        }
        return orderRepository.save(order);
    }

    public Order updateOrder(Long id, OrderRequest request) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id));

        if (request.getCustomerName() != null) order.setCustomerName(request.getCustomerName());
        if (request.getProductName()  != null) order.setProductName(request.getProductName());

        if (request.getStatus() != null) {
            validateStatusTransition(order.getStatus(), request.getStatus());
            order.setStatus(request.getStatus());
        }

        return orderRepository.save(order);
    }

    public void deleteOrder(Long id) {
        if (!orderRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found: " + id);
        }
        orderRepository.deleteById(id);
    }

    private void validateStatusTransition(String current, String next) {
        if (!VALID_STATUSES.contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status '" + next + "'. Allowed values: pending, shipped, delivered.");
        }
        if (current.equals(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Order is already '" + current + "'. Please select a different status.");
        }

        Set<String> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, buildTransitionErrorMessage(current, next));
        }
    }

    private String buildTransitionErrorMessage(String current, String next) {
        if ("pending".equals(current) && "delivered".equals(next)) {
            return "Order must be shipped before it can be marked as delivered. " +
                   "Please update the status to 'shipped' first.";
        }
        if ("delivered".equals(current)) {
            return "Order is already delivered and cannot be changed.";
        }
        return "Invalid status transition: '" + current + "' → '" + next + "'. " +
               "Allowed: pending → shipped → delivered.";
    }
}
