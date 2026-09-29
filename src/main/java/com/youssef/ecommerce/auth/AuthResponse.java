package com.youssef.ecommerce.auth;

public record AuthResponse(
        String token,
        Long userId,
        String firstName,
        String lastName,
        String email
) {
}
