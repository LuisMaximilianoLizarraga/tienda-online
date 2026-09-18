package com.lucho.tienda.security;

import com.lucho.tienda.exception.CustomAccessDeniedHandler;
import com.lucho.tienda.service.CartService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static com.lucho.tienda.constant.ApiEndpointConstants.*;
import static com.lucho.tienda.constant.ApiFieldConstants.*;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private CartService cartService;

    // Security Mocks required by SecurityConfig injection
    @MockBean
    private CustomAccessDeniedHandler accessDeniedHandler;

    @Test
    void publicEndpoints_AccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void protectedEndpoints_AccessibleWhenAuthenticated() throws Exception {
        String requestUri = (ENDPOINT_CARTS + SUB_ENDPOINT_GET_CART)
                .replace("{" + PARAM_CART_ID + "}", "1");

        mockMvc.perform(get(requestUri))
                .andExpect(status().isOk());
    }
}