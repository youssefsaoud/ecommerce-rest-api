package com.youssef.ecommerce.order;

import com.youssef.ecommerce.cart.Cart;
import com.youssef.ecommerce.cart.CartItem;
import com.youssef.ecommerce.cart.CartRepository;
import com.youssef.ecommerce.exception.EmptyCartException;
import com.youssef.ecommerce.exception.ForbiddenOperationException;
import com.youssef.ecommerce.exception.InsufficientStockException;
import com.youssef.ecommerce.exception.InvalidCartOperationException;
import com.youssef.ecommerce.exception.ResourceNotFoundException;
import com.youssef.ecommerce.product.Product;
import com.youssef.ecommerce.product.ProductRepository;
import com.youssef.ecommerce.user.User;
import com.youssef.ecommerce.user.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final EntityManager entityManager;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        UserRepository userRepository,
                        ProductRepository productRepository,
                        EntityManager entityManager) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Order checkout(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        Cart cart = cartRepository.findByUserForUpdate(user).orElse(null);

        if (cart == null || cart.getItems().isEmpty()) {
            throw new EmptyCartException("Cart is empty");
        }

        // The cart lock serializes repeated checkout requests for the same cart.
        // Lock distinct products in ID order across every checkout to reduce deadlocks.
        List<Product> products = cart.getItems().stream().map(CartItem::getProduct)
                .distinct().sorted(Comparator.comparing(Product::getId)).toList();
        for (Product product : products) {
            // Products may already be managed after loading the cart. Refresh under the
            // lock so a waiting transaction sees the preceding buyer's committed stock.
            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        }

        Map<Long, Long> requestedQuantities = new TreeMap<>();
        for (CartItem item : cart.getItems()) {
            if (item.getQuantity() == null || item.getQuantity() <= 0) {
                throw new InvalidCartOperationException("Cart quantity must be positive");
            }
            requestedQuantities.merge(item.getProduct().getId(), item.getQuantity().longValue(), Long::sum);
        }
        for (Product product : products) {
            if (product.getQuantity() < requestedQuantities.get(product.getId())) {
                throw new InsufficientStockException("Not enough stock for product: " + product.getName());
            }
        }

        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(BigDecimal.ZERO);

        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPrice(product.getPrice());

            order.getItems().add(orderItem);

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            totalAmount = totalAmount.add(lineTotal);

            product.setQuantity(product.getQuantity() - cartItem.getQuantity());
            productRepository.save(product);
        }

        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);

        cart.getItems().clear();
        cartRepository.save(cart);

        return savedOrder;
    }

    @Transactional
    public Order checkoutByEmail(String email) {
        User user = getUserByEmail(email);
        return checkout(user.getId());
    }

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    public Order getOrderByIdForUser(String email, Long orderId) {
        User user = getUserByEmail(email);
        Order order = getOrderById(orderId);

        if (!order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenOperationException("Access denied");
        }

        return order;
    }

    public List<Order> getOrdersByUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return orderRepository.findByUser(user);
    }

    public List<Order> getOrdersByUserEmail(String email) {
        User user = getUserByEmail(email);
        return orderRepository.findByUser(user);
    }

    private User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
