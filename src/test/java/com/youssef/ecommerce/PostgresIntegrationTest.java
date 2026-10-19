package com.youssef.ecommerce;

import com.youssef.ecommerce.order.OrderService;
import com.youssef.ecommerce.exception.InsufficientStockException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"jwt.secret=isolated-tests-only-signing-key-at-least-32-bytes",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false"})
@AutoConfigureMockMvc
@EnabledIf("databaseAvailable")
class PostgresIntegrationTest {
    private static PostgreSQLContainer postgres;
    // Optional fallback only for a dedicated, disposable cluster. The normal test path uses Docker.
    private static final String ISOLATED_URL = System.getProperty("isolated.postgres.url");

    static boolean databaseAvailable() {
        if (ISOLATED_URL != null) {
            if (!ISOLATED_URL.equals("jdbc:postgresql://127.0.0.1:55439/ecommerce_integration")) {
                throw new IllegalArgumentException("Only the isolated integration database on port 55439 is allowed");
            }
            return true;
        }
        return DockerClientFactory.instance().isDockerAvailable();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        if (ISOLATED_URL != null) {
            registry.add("spring.datasource.url", () -> ISOLATED_URL);
            registry.add("spring.datasource.username", () -> "integration");
            registry.add("spring.datasource.password", () -> "");
        } else {
            postgres = new PostgreSQLContainer("postgres:18");
            postgres.start();
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) postgres.stop();
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired OrderService orders;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Flyway flyway;

    @BeforeEach
    void resetDisposableDatabase() {
        // This datasource is always the disposable test database, never application.properties.
        jdbc.execute("TRUNCATE order_item, orders, cart_item, cart, product, category, users RESTART IDENTITY CASCADE");
    }

    @Test
    void flywayInitializesSchemaAndHibernateValidatesIt() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("1");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema='public' and table_name in ('users','category','product','cart','cart_item','orders','order_item')", Integer.class)).isEqualTo(7);
    }

    @Test
    void concurrentBuyersCannotBothPurchaseFinalUnit() throws Exception {
        jdbc.update("insert into users(id,email) values (1,'one@test.example'),(2,'two@test.example')");
        jdbc.update("insert into product(id,name,price,quantity) values (1,'Last mouse',25.50,1)");
        jdbc.update("insert into cart(id,user_id) values (1,1),(2,2)");
        jdbc.update("insert into cart_item(cart_id,product_id,quantity) values (1,1,1),(2,1,1)");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.createStatement()) {
                statement.executeQuery("select id from product where id=1 for update").close();
            }
            Future<Boolean> first = pool.submit(() -> checkout(1L));
            Future<Boolean> second = pool.submit(() -> checkout(2L));
            try {
                // Hold the row until BOTH independent checkouts reach PostgreSQL's lock
                // queue. This proves real overlap rather than relying on thread timing.
                long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
                int waiting = 0;
                while (waiting < 2 && System.nanoTime() < deadline) {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock' and query like '%product%'", Integer.class);
                    LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
                }
                assertThat(waiting).as("both checkouts waiting for a real PostgreSQL row lock").isEqualTo(2);
            } finally {
                blocker.commit();
            }
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(jdbc.queryForObject("select quantity from product where id=1", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select total_amount from orders", java.math.BigDecimal.class)).isEqualByComparingTo("25.50");
        assertThat(jdbc.queryForObject("select price from order_item", java.math.BigDecimal.class)).isEqualByComparingTo("25.50");
        assertThat(jdbc.queryForObject("select count(*) from cart_item", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from cart_item ci join cart c on ci.cart_id=c.id join orders o on c.user_id=o.user_id", Integer.class)).isZero();
    }

    private boolean checkout(Long userId) {
        try {
            orders.checkout(userId);
            return true;
        } catch (InsufficientStockException expected) {
            return false;
        }
    }

    @Test
    void jwtCartCheckoutHistoryAndOwnershipWorkOverHttp() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/checkout")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
        String owner = register("owner@test.example");
        String other = register("other@test.example");
        assertThat(jdbc.queryForObject("select password from users where email='owner@test.example'", String.class))
                .startsWith("$2").isNotEqualTo("test-password");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@test.example\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
        String login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@test.example\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        owner = "Bearer " + json.readTree(login).get("token").asText();
        jdbc.update("insert into product(id,name,price,quantity) values (1,'Mouse',25.50,4)");
        mvc.perform(post("/api/cart/items").header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        String cart = mvc.perform(get("/api/cart").header("Authorization", owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long itemId = json.readTree(cart).get("items").get(0).get("id").asLong();
        mvc.perform(put("/api/cart/items/" + itemId).header("Authorization", other)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/cart/items/" + itemId).header("Authorization", other))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/cart").header("Authorization", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        String checkout = mvc.perform(post("/api/orders/checkout").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalAmount").value(51.00))
                .andExpect(jsonPath("$.items[0].price").value(25.50))
                .andReturn().getResponse().getContentAsString();
        long orderId = json.readTree(checkout).get("id").asLong();
        mvc.perform(get("/api/cart").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        assertThat(jdbc.queryForObject("select quantity from product where id=1", Integer.class)).isEqualTo(2);
        jdbc.update("update product set price=30 where id=1");
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].price").value(25.50));
        mvc.perform(get("/api/orders/my-orders").header("Authorization", owner))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(orderId));
        mvc.perform(get("/api/orders/" + orderId).header("Authorization", other))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void insufficientStockRollsBackAndReturnsConflict() throws Exception {
        String token = register("short-stock@test.example");
        jdbc.update("insert into product(id,name,price,quantity) values (1,'Mouse',25.50,1)");
        mvc.perform(post("/api/cart/items").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"quantity\":2}")).andExpect(status().isOk());
        mvc.perform(post("/api/orders/checkout").header("Authorization", token))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Not enough stock for product: Mouse"));
        assertThat(jdbc.queryForObject("select count(*) from orders", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select quantity from product where id=1", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from cart_item", Integer.class)).isEqualTo(2);
    }

    @Test
    void productsAreSortedAndPagedInPostgres() throws Exception {
        jdbc.update("insert into product(name,price,quantity) values ('C',30,1),('A',10,1),('B',20,1)");
        mvc.perform(get("/api/products?page=1&size=1&sort=price,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].name").value("B"))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3));
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Test\",\"lastName\":\"Buyer\",\"email\":\"" + email + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(response).get("token").asText();
    }
}
