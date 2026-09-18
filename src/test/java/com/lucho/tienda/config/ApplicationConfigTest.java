package com.lucho.tienda.config;

import com.lucho.tienda.model.User;
import com.lucho.tienda.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationConfigTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApplicationConfig applicationConfig;

    @Test
    void userDetailsService_LoadsUserSuccessfully() {
        User user = new User();
        user.setUsername("lucho");
        user.setPassword("secret");

        when(userRepository.findByUsername("lucho")).thenReturn(Optional.of(user));

        UserDetailsService service = applicationConfig.userDetailsService();
        UserDetails details = service.loadUserByUsername("lucho");

        assertNotNull(details);
        assertEquals("lucho", details.getUsername());
        assertEquals("secret", details.getPassword());
    }

    @Test
    void userDetailsService_ThrowsUsernameNotFoundException() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        UserDetailsService service = applicationConfig.userDetailsService();

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("unknown"));
    }

    @Test
    void passwordEncoder_ReturnsBCryptInstance() {
        PasswordEncoder encoder = applicationConfig.passwordEncoder();
        assertNotNull(encoder);

        String encoded = encoder.encode("password");
        assertTrue(encoder.matches("password", encoded));
    }
}