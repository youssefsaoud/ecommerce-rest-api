package com.youssef.ecommerce.cart;

import jakarta.validation.Valid;
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
    public CartResponse getUserCart(@PathVariable Long userId) {
        return toResponse(cartService.getUserCart(userId));
    }

    @PostMapping("/{userId}/items")
    public CartResponse addProductToCart(@PathVariable Long userId, @Valid @RequestBody AddCartItemRequest request) {
        return toResponse(cartService.addProductToCart(userId, request.productId(), request.quantity()));
    }

    @PutMapping("/{userId}/items/{cartItemId}")
    public CartResponse updateCartItemQuantity(@PathVariable Long userId,
                                               @PathVariable Long cartItemId,
                                               @Valid @RequestBody UpdateCartItemRequest request) {
        return toResponse(cartService.updateCartItemQuantity(userId, cartItemId, request.quantity()));
    }

    @DeleteMapping("/{userId}/items/{cartItemId}")
    public CartResponse removeItemFromCart(@PathVariable Long userId, @PathVariable Long cartItemId) {
        return toResponse(cartService.removeItemFromCart(userId, cartItemId));
    }

    @DeleteMapping("/{userId}/items")
    public CartResponse clearCart(@PathVariable Long userId) {
        return toResponse(cartService.clearCart(userId));
    }

    private CartResponse toResponse(Cart cart) {
        return new CartResponse(
                cart.getId(),
                cart.getUser().getId(),
                cart.getItems()
                        .stream()
                        .map(item -> new CartItemResponse(
                                item.getId(),
                                item.getProduct().getId(),
                                item.getProduct().getName(),
                                item.getProduct().getPrice(),
                                item.getQuantity()
                        ))
                        .toList()
        );
    }
}
