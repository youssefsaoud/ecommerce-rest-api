package com.youssef.ecommerce.auth;

import com.youssef.ecommerce.exception.DuplicateEmailException;
import com.youssef.ecommerce.exception.InvalidLoginException;
import com.youssef.ecommerce.security.JwtService;
import com.youssef.ecommerce.user.User;
import com.youssef.ecommerce.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerHashesPasswordAndReturnsTokenWithoutPassword() {
        RegisterRequest request = new RegisterRequest("Youssef", "Dev", "youssef@example.com", "secret");

        when(userRepository.findByEmail("youssef@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(5L);
            return user;
        });
        when(jwtService.generateToken("youssef@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.userId()).isEqualTo(5L);
        assertThat(response.email()).isEqualTo("youssef@example.com");
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Youssef", "Dev", "youssef@example.com", "secret");
        when(userRepository.findByEmail("youssef@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = new User();
        user.setEmail("youssef@example.com");
        user.setPassword("bcrypt-hash");

        when(userRepository.findByEmail("youssef@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);

        LoginRequest request = new LoginRequest("youssef@example.com", "wrong");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(InvalidLoginException.class);
    }
}
