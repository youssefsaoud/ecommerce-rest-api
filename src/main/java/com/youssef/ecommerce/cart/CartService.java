package com.youssef.ecommerce.cart;

import com.youssef.ecommerce.product.Product;
import com.youssef.ecommerce.product.ProductRepository;
import com.youssef.ecommerce.user.User;
import com.youssef.ecommerce.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       UserRepository userRepository,
                       ProductRepository productRepository) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public Cart getUserCart(Long userId) {
        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            return null;
        }

        Cart cart = cartRepository.findByUser(user).orElse(null);

        if (cart == null) {
            cart = new Cart();
            cart.setUser(user);
            cart = cartRepository.save(cart);
        }

        return cart;
    }

    @Transactional
    public Cart addProductToCart(Long userId, Long productId, Integer quantity) {
        Cart cart = getUserCart(userId);
        Product product = productRepository.findById(productId).orElse(null);

        if (cart == null || product == null || quantity == null || quantity <= 0) {
            return null;
        }

        for (CartItem item : cart.getItems()) {
            if (item.getProduct().getId().equals(productId)) {
                item.setQuantity(item.getQuantity() + quantity);
                return cartRepository.save(cart);
            }
        }

        CartItem cartItem = new CartItem();
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(quantity);

        cart.getItems().add(cartItem);

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart updateCartItemQuantity(Long userId, Long cartItemId, Integer quantity) {
        Cart cart = getUserCart(userId);
        CartItem cartItem = cartItemRepository.findById(cartItemId).orElse(null);

        if (cart == null || cartItem == null || quantity == null || quantity <= 0) {
            return null;
        }

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            return null;
        }

        cartItem.setQuantity(quantity);
        cartItemRepository.save(cartItem);

        return cart;
    }

    @Transactional
    public Cart removeItemFromCart(Long userId, Long cartItemId) {
        Cart cart = getUserCart(userId);
        CartItem cartItem = cartItemRepository.findById(cartItemId).orElse(null);

        if (cart == null || cartItem == null) {
            return null;
        }

        if (!cartItem.getCart().getId().equals(cart.getId())) {
            return null;
        }

        cart.getItems().remove(cartItem);

        return cartRepository.save(cart);
    }

    @Transactional
    public Cart clearCart(Long userId) {
        Cart cart = getUserCart(userId);

        if (cart == null) {
            return null;
        }

        cart.getItems().clear();

        return cartRepository.save(cart);
    }
}
