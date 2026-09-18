package com.lucho.tienda.service.impl;

import com.lucho.tienda.dto.AuthRequest;
import com.lucho.tienda.dto.AuthResponse;
import com.lucho.tienda.exception.ResourceNotFoundException;
import com.lucho.tienda.model.User;
import com.lucho.tienda.repository.UserRepository;
import com.lucho.tienda.security.JwtUtils;
import com.lucho.tienda.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import static com.lucho.tienda.constant.ErrorMessageConstants.*;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;

    @Override
    public AuthResponse login(AuthRequest request) {

        // Delegate authentication to the Spring Security provider chain
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        // Fetch the fully populated User entity from the database to retrieve the ID
        // Note: We only reach this point if the authentication was successful
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException(USER_NOT_FOUND));

        // Generate the JWT token including both username and userId as claims
        String token = jwtUtils.generateToken(user.getUsername(), user.getId());

        return new AuthResponse(token);
    }
}