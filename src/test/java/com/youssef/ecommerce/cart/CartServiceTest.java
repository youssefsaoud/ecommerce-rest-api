package com.youssef.ecommerce.cart;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CartService cartService;

    @Test
    void addProductToCartCreatesCartItem() {
        User user = user(1L);
        Cart cart = cart(user);
        Product product = product(10L, "Keyboard", "99.99", 5);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.addProductToCart(1L, 10L, 2);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getProduct()).isEqualTo(product);
        assertThat(result.getItems().getFirst().getQuantity()).isEqualTo(2);
    }

    @Test
    void addSameProductIncrementsQuantityInsteadOfCreatingDuplicate() {
        User user = user(1L);
        Cart cart = cart(user);
        Product product = product(10L, "Keyboard", "99.99", 5);
        CartItem item = new CartItem();
        item.setId(100L);
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(1);
        cart.getItems().add(item);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUser(user)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.addProductToCart(1L, 10L, 3);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().getFirst().getQuantity()).isEqualTo(4);
    }

    private User user(Long id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        return user;
    }

    private Cart cart(User user) {
        Cart cart = new Cart();
        cart.setId(50L);
        cart.setUser(user);
        cart.setItems(new ArrayList<>());
        return cart;
    }

    private Product product(Long id, String name, String price, Integer quantity) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setQuantity(quantity);
        return product;
    }
}
