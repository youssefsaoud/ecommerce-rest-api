package com.youssef.ecommerce.order;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/checkout")
    public OrderResponse checkout(Authentication authentication) {
        return toResponse(orderService.checkoutByEmail(authentication.getName()));
    }

    @GetMapping("/{orderId}")
    public OrderResponse getOrderById(Authentication authentication, @PathVariable Long orderId) {
        return toResponse(orderService.getOrderByIdForUser(authentication.getName(), orderId));
    }

    @GetMapping("/my-orders")
    public List<OrderResponse> getOrdersByUser(Authentication authentication) {
        return orderService.getOrdersByUserEmail(authentication.getName())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUser().getId(),
                order.getItems()
                        .stream()
                        .map(item -> new OrderItemResponse(
                                item.getId(),
                                item.getProduct().getId(),
                                item.getProduct().getName(),
                                item.getQuantity(),
                                item.getPrice()
                        ))
                        .toList(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
