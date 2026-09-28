package com.youssef.ecommerce.user;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email
) {
}
