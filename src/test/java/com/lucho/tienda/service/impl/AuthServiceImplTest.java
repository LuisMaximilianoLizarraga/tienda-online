package com.lucho.tienda.service.impl;

import com.lucho.tienda.dto.AuthRequest;
import com.lucho.tienda.dto.AuthResponse;
import com.lucho.tienda.exception.ResourceNotFoundException;
import com.lucho.tienda.model.User;
import com.lucho.tienda.repository.UserRepository;
import com.lucho.tienda.security.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthServiceImpl authService;

    private AuthRequest validRequest;
    private User mockUser;

    @BeforeEach
    void setUp() {
        // Initialize test data before each test
        validRequest = new AuthRequest("testuser@mail.com", "password123");

        mockUser = User.builder()
                .id(1L)
                .username("testuser@mail.com")
                .password("encodedPassword")
                .build();
    }

    @Test
    @DisplayName("Should return AuthResponse with token when login is successful")
    void login_Success_ReturnsToken() {
        // Arrange: Mock the AuthenticationManager to do nothing (simulate success)
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);

        // Arrange: Mock the DB to return our user when searched by username
        when(userRepository.findByUsername(validRequest.getUsername()))
                .thenReturn(Optional.of(mockUser));

        // Arrange: Mock JwtUtils to return a fake token using both username and ID
        String MOCK_TOKEN = "mocked.jwt.token";
        when(jwtUtils.generateToken(mockUser.getUsername(), mockUser.getId()))
                .thenReturn(MOCK_TOKEN);

        // Act: Call the method we are testing
        AuthResponse response = authService.login(validRequest);

        // Assert: Verify the response is not null and contains the expected token
        assertNotNull(response);
        assertEquals(MOCK_TOKEN, response.getToken());

        // Assert: Verify interactions to ensure our flow was respected
        verify(authenticationManager, times(1)).authenticate(any());
        verify(userRepository, times(1)).findByUsername(validRequest.getUsername());
        verify(jwtUtils, times(1)).generateToken(mockUser.getUsername(), mockUser.getId());
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when user exists in auth but not in DB")
    void login_UserNotInDB_ThrowsException() {
        // Arrange: Simulate successful auth but user missing in DB (edge case)
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(null);

        when(userRepository.findByUsername(validRequest.getUsername()))
                .thenReturn(Optional.empty());

        // Act & Assert: Expect an exception to be thrown
        assertThrows(ResourceNotFoundException.class, () -> authService.login(validRequest));

        // Assert: Verify token generation was NEVER called
        verify(jwtUtils, never()).generateToken(anyString(), anyLong());
    }
}