package com.lucho.tienda.security;

import com.lucho.tienda.constant.ApiEndpointConstants;
import com.lucho.tienda.exception.CustomAccessDeniedHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    @Autowired
    private CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Disable CSRF protection (Safe and recommended for Stateless REST APIs using JWT)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Enable frame options (Required for the H2 Console UI to render correctly)
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))

                // 3. Configure endpoint authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Explicitly allow access to the H2 Console
                        .requestMatchers(PathRequest.toH2Console()).permitAll()

                        // Allow access to favicon to prevent unnecessary 403 errors in server logs
                        .requestMatchers("/favicon.ico").permitAll()

                        // Allow access to predefined public endpoints (e.g., Auth, Swagger, Actuator)
                        .requestMatchers(ApiEndpointConstants.PUBLIC_PATHS).permitAll()

                        // Require authentication for any other request
                        .anyRequest().authenticated()
                )

        // 4. IMPORTANT: Temporarily comment out this line if the H2 Console login fails or loops.
        // H2 Console relies on sessions, which conflicts with a strict STATELESS policy.
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}