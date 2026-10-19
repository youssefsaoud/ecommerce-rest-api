package com.youssef.ecommerce.product;

import com.youssef.ecommerce.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductControllerTest {
    private ProductService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        service = mock(ProductService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void defaultsReturnDtoContentAndPageMetadata() throws Exception {
        Product product = new Product(4L, "Mouse", "USB", new BigDecimal("25.50"), 8, null);
        when(service.getAllProducts(any(Pageable.class))).thenAnswer(call -> {
            Pageable page = call.getArgument(0);
            assertThat(page.getPageNumber()).isZero();
            assertThat(page.getPageSize()).isEqualTo(10);
            assertThat(page.getSort().getOrderFor("id").isAscending()).isTrue();
            return new PageImpl<>(List.of(product), page, 1);
        });
        mvc.perform(get("/api/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Mouse"))
                .andExpect(jsonPath("$.content[0].quantity").value(8))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void passesRequestedPageAndPriceSortToSpringData() throws Exception {
        when(service.getAllProducts(any(Pageable.class))).thenAnswer(call -> {
            Pageable page = call.getArgument(0);
            assertThat(page.getPageNumber()).isEqualTo(2);
            assertThat(page.getPageSize()).isEqualTo(3);
            assertThat(page.getSort().getOrderFor("price").isDescending()).isTrue();
            assertThat(page.getSort().getOrderFor("id")).isNotNull();
            return new PageImpl<Product>(List.of(), page, 0);
        });
        mvc.perform(get("/api/products?page=2&size=3&sort=price,desc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.number").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=101", "page=abc", "size=2147483648",
            "sort=category.name,asc", "sort=price,sideways", "sort=price", "sort=price,asc,extra"})
    void rejectsInvalidPaginationAndSorting(String query) throws Exception {
        mvc.perform(get("/api/products?" + query)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
