package com.youssef.ecommerce.cart;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
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

    @GetMapping
    public CartResponse getUserCart(Authentication authentication) {
        return toResponse(cartService.getUserCartByEmail(authentication.getName()));
    }

    @PostMapping("/items")
    public CartResponse addProductToCart(Authentication authentication, @Valid @RequestBody AddCartItemRequest request) {
        return toResponse(cartService.addProductToCartByEmail(authentication.getName(), request.productId(), request.quantity()));
    }

    @PutMapping("/items/{cartItemId}")
    public CartResponse updateCartItemQuantity(Authentication authentication,
                                               @PathVariable Long cartItemId,
                                               @Valid @RequestBody UpdateCartItemRequest request) {
        return toResponse(cartService.updateCartItemQuantityByEmail(authentication.getName(), cartItemId, request.quantity()));
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartResponse removeItemFromCart(Authentication authentication, @PathVariable Long cartItemId) {
        return toResponse(cartService.removeItemFromCartByEmail(authentication.getName(), cartItemId));
    }

    @DeleteMapping("/items")
    public CartResponse clearCart(Authentication authentication) {
        return toResponse(cartService.clearCartByEmail(authentication.getName()));
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
