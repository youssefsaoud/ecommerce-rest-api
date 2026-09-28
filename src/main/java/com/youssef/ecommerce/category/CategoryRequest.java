package com.youssef.ecommerce.category;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank(message = "Name is required")
        String name
) {
}
