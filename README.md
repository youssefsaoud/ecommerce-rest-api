# E-commerce REST API

A Spring Boot REST API for a small e-commerce backend. The project covers product management, categories, users, cart operations, checkout, order history, validation, global error handling, and JWT authentication.

This is built as a junior backend portfolio project, with a simple layered architecture and readable business logic.

## Features

- Product and category CRUD
- User creation and profile-style CRUD
- BCrypt password hashing
- JWT register/login authentication
- Stateless protected endpoints with Bearer tokens
- Cart operations: add item, update quantity, remove item, clear cart
- Transactional checkout
- Product stock validation and deduction during checkout
- Order history with checkout price snapshots
- DTO-based API responses
- Bean Validation for request bodies
- Global JSON error responses
- Swagger UI / OpenAPI documentation

## Technologies

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- Spring Security
- PostgreSQL
- Lombok
- JJWT
- Springdoc OpenAPI
- JUnit 5
- Mockito

## Architecture

The project follows a straightforward Spring structure:

```text
Controller -> Service -> Repository -> PostgreSQL
```

- Controllers handle HTTP requests and DTO responses.
- Services contain business rules such as cart ownership, checkout, and stock deduction.
- Repositories use Spring Data JPA.
- Entities map to PostgreSQL tables.

## Authentication Flow

1. A user registers or logs in through `/api/auth`.
2. The API returns a JWT.
3. The client sends the token as:

```http
Authorization: Bearer <token>
```

4. The JWT filter validates the token and loads the user by email.
5. Protected endpoints use the authenticated user instead of trusting client-supplied user IDs.

Passwords are stored as BCrypt hashes. JWT authentication is stateless.

## Main Endpoint Groups

- Auth: `/api/auth/register`, `/api/auth/login`
- Products: `/api/products`
- Categories: `/api/categories`
- Users: `/api/users`
- Cart: `/api/cart`
- Orders: `/api/orders`

Public endpoints:

- `/api/auth/**`
- `GET /api/products/**`
- `GET /api/categories/**`
- Swagger/OpenAPI endpoints

Protected endpoints require a valid JWT.

## Checkout

Checkout is transactional. The API:

1. Loads the authenticated user's cart.
2. Rejects checkout if the cart is empty.
3. Checks product stock for every cart item.
4. Creates an order and order items.
5. Copies each product's current price into the order item.
6. Calculates the order total using `BigDecimal`.
7. Deducts product stock.
8. Clears the cart while keeping the cart record.

If stock is insufficient, checkout fails and the transaction rolls back.

## Required Environment Variables

Set these before running the application:

```text
DB_PASSWORD=your_postgres_password
JWT_SECRET=your_long_jwt_signing_secret
```

Do not commit real secrets to source control.

## Run Locally

Create a PostgreSQL database named:

```text
ecommerce_db
```

Then run:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

## Run Tests

```bash
./mvnw test
```

On Windows:

```bash
mvnw.cmd test
```

The included service tests use Mockito and do not require PostgreSQL.

## Swagger UI

After starting the app, open:

```text
http://localhost:8080/swagger-ui.html
```

OpenAPI JSON is available at:

```text
http://localhost:8080/v3/api-docs
```

Use the Swagger UI **Authorize** button with a JWT from `/api/auth/login` or `/api/auth/register` to test protected endpoints.
