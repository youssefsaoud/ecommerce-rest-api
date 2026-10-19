package com.youssef.ecommerce.order;

import com.youssef.ecommerce.cart.Cart;
import com.youssef.ecommerce.cart.CartItem;
import com.youssef.ecommerce.cart.CartRepository;
import com.youssef.ecommerce.exception.ForbiddenOperationException;
import com.youssef.ecommerce.exception.InsufficientStockException;
import com.youssef.ecommerce.product.Product;
import com.youssef.ecommerce.product.ProductRepository;
import com.youssef.ecommerce.user.User;
import com.youssef.ecommerce.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CartRepository cartRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private OrderService orderService;

    @Test
    void checkoutCreatesOrderCalculatesTotalDeductsStockAndClearsCart() {
        User user = user(3L, "user3@example.com");
        Product product = product(7L, "Mouse", "25.50", 10);
        Cart cart = cartWithItem(user, product, 2);

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserForUpdate(user)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Order order = orderService.checkout(3L);

        assertThat(order.getUser()).isEqualTo(user);
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getItems().getFirst().getPrice()).isEqualByComparingTo("25.50");
        assertThat(order.getTotalAmount()).isEqualByComparingTo("51.00");
        assertThat(product.getQuantity()).isEqualTo(8);
        assertThat(cart.getItems()).isEmpty();
        verify(productRepository).save(product);
        verify(cartRepository).save(cart);
    }

    @Test
    void insufficientStockPreventsCheckout() {
        User user = user(3L, "user3@example.com");
        Product product = product(7L, "Mouse", "25.50", 1);
        Cart cart = cartWithItem(user, product, 2);

        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserForUpdate(user)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.checkout(3L))
                .isInstanceOf(InsufficientStockException.class);

        verify(orderRepository, never()).save(any(Order.class));
        verify(productRepository, never()).save(any(Product.class));
        assertThat(product.getQuantity()).isEqualTo(1);
        assertThat(cart.getItems()).hasSize(1);
    }

    @Test
    void userCannotRetrieveAnotherUsersOrder() {
        User authenticatedUser = user(3L, "user3@example.com");
        User orderOwner = user(1L, "user1@example.com");
        Order order = new Order();
        order.setId(99L);
        order.setUser(orderOwner);

        when(userRepository.findByEmail("user3@example.com")).thenReturn(Optional.of(authenticatedUser));
        when(orderRepository.findById(99L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.getOrderByIdForUser("user3@example.com", 99L))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void checkoutLocksProductsInIdOrderBeforeDeductingStock() {
        User user = user(3L, "user3@example.com");
        Product higherId = product(7L, "Mouse", "25.50", 10);
        Product lowerId = product(2L, "Cable", "10.00", 5);
        Cart cart = cartWithItem(user, higherId, 2);
        CartItem second = new CartItem();
        second.setCart(cart);
        second.setProduct(lowerId);
        second.setQuantity(1);
        cart.getItems().add(second);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserForUpdate(user)).thenReturn(Optional.of(cart));
        when(orderRepository.save(any(Order.class))).thenAnswer(call -> call.getArgument(0));

        Order order = orderService.checkout(3L);

        var sequence = org.mockito.Mockito.inOrder(entityManager, productRepository);
        sequence.verify(entityManager).refresh(lowerId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        sequence.verify(entityManager).refresh(higherId, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        sequence.verify(productRepository).save(higherId);
        sequence.verify(productRepository).save(lowerId);
        assertThat(order.getTotalAmount()).isEqualByComparingTo("61.00");
    }

    @Test
    void duplicateCartLinesCannotBypassStockValidation() {
        User user = user(3L, "user3@example.com");
        Product product = product(7L, "Mouse", "25.50", 1);
        Cart cart = cartWithItem(user, product, 1);
        CartItem duplicate = new CartItem();
        duplicate.setCart(cart);
        duplicate.setProduct(product);
        duplicate.setQuantity(1);
        cart.getItems().add(duplicate);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserForUpdate(user)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.checkout(3L)).isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any(Order.class));
        assertThat(product.getQuantity()).isEqualTo(1);
    }

    private User user(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    private Product product(Long id, String name, String price, Integer quantity) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setQuantity(quantity);
        return product;
    }

    private Cart cartWithItem(User user, Product product, Integer quantity) {
        Cart cart = new Cart();
        cart.setId(20L);
        cart.setUser(user);
        cart.setItems(new ArrayList<>());

        CartItem item = new CartItem();
        item.setId(30L);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(quantity);

        cart.getItems().add(item);
        return cart;
    }
}
