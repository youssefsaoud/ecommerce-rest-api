package com.youssef.ecommerce.cart;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{userId}")
    public Cart getUserCart(@PathVariable Long userId) {
        return cartService.getUserCart(userId);
    }

    @PostMapping("/{userId}/items")
    public Cart addProductToCart(@PathVariable Long userId, @RequestBody AddCartItemRequest request) {
        return cartService.addProductToCart(userId, request.productId(), request.quantity());
    }

    @PutMapping("/{userId}/items/{cartItemId}")
    public Cart updateCartItemQuantity(@PathVariable Long userId,
                                       @PathVariable Long cartItemId,
                                       @RequestBody UpdateCartItemRequest request) {
        return cartService.updateCartItemQuantity(userId, cartItemId, request.quantity());
    }

    @DeleteMapping("/{userId}/items/{cartItemId}")
    public Cart removeItemFromCart(@PathVariable Long userId, @PathVariable Long cartItemId) {
        return cartService.removeItemFromCart(userId, cartItemId);
    }

    @DeleteMapping("/{userId}/items")
    public Cart clearCart(@PathVariable Long userId) {
        return cartService.clearCart(userId);
    }

    public record AddCartItemRequest(Long productId, Integer quantity) {
    }

    public record UpdateCartItemRequest(Integer quantity) {
    }
}
