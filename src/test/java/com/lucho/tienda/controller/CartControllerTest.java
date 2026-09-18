package com.lucho.tienda.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucho.tienda.dto.AddProductRequest;
import com.lucho.tienda.dto.ProductOperationRequest;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Category;
import com.lucho.tienda.model.Product;
import com.lucho.tienda.model.enums.CartStatus;
import com.lucho.tienda.security.JwtAuthenticationFilter;
import com.lucho.tienda.security.JwtUtils;
import com.lucho.tienda.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static com.lucho.tienda.constant.ApiEndpointConstants.*;
import static com.lucho.tienda.constant.ApiFieldConstants.*;

@WebMvcTest(
        controllers = CartController.class,
        excludeAutoConfiguration = {SecurityAutoConfiguration.class}
)
@WithMockUser
@AutoConfigureMockMvc(addFilters = false)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    @MockBean
    private JwtUtils jwtUtils;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private Cart cart;
    private final String MOCK_TOKEN = "Bearer mock-jwt-token";

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setId(1L);
        product.setCode("P01");
        product.setName("Whey Protein");
        product.setCategory(new Category());
        product.setPrice(new BigDecimal("1000.00"));

        cart = new Cart();
        cart.setId(1L);
        cart.setStatus(CartStatus.CREATED);
        cart.setTotalAmount(new BigDecimal("2000.00"));
        cart.setItems(new HashSet<>() {
        });
        CartItem cartItem = new CartItem();
        cartItem.setId(1L);
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(2);
        cart.getItems().add(cartItem);
    }

    @Test
    @DisplayName("Should create cart successfully using userId from JWT token")
    void createCart_Returns201_WhenSuccessful() throws Exception {
        // Arrange
        when(jwtUtils.extractUserIdFromHeader(anyString())).thenReturn(1L);
        when(cartService.createCart(1L)).thenReturn(cart);

        // Act & Assert: Perform POST with Authorization header ONLY (No body needed!)
        mockMvc.perform(post(ENDPOINT_CARTS)
                        .header(HttpHeaders.AUTHORIZATION, MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    @DisplayName("Should return user carts extracting user identity from JWT")
    void getUserCarts_ReturnsListOfCartResponses() throws Exception {
        when(jwtUtils.extractUserIdFromHeader(anyString())).thenReturn(1L);
        when(cartService.getUserCarts(1L)).thenReturn(List.of(cart));

        mockMvc.perform(get(ENDPOINT_CARTS, 1L)
                        .header(HttpHeaders.AUTHORIZATION, MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }
    @Test
    @DisplayName("Should add product to cart successfully")
    void addProduct_Returns200_WhenSuccessful() throws Exception {
        AddProductRequest request = new AddProductRequest("P01");
        when(cartService.addProduct(any(ProductOperationRequest.class))).thenReturn(cart);

        String requestUri = FULL_ENDPOINT_CART_PRODUCTS.replace("{" + PARAM_CART_ID + "}", "1");

        mockMvc.perform(post(requestUri)
                        .header(HttpHeaders.AUTHORIZATION, MOCK_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.items[0]." + FIELD_PRODUCT_CODE).value("P01"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void removeProduct_Returns200_WhenSuccessful() throws Exception {
        when(cartService.removeProduct(any(ProductOperationRequest.class))).thenReturn(cart);

        String requestUri = (ENDPOINT_CARTS + SUB_ENDPOINT_REMOVE_PRODUCT)
                .replace("{" + PARAM_CART_ID + "}", "1")
                .replace("{" + FIELD_PRODUCT_CODE + "}", "P01");

        mockMvc.perform(delete(requestUri)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void getCartProducts_ReturnsListOfCartItemResponses() throws Exception {
        when(cartService.getCartProducts(any())).thenReturn(
                cart.getItems().stream()
                        .sorted(Comparator.comparing(CartItem::getId))
                        .toList());

        String requestUri = FULL_ENDPOINT_CART_PRODUCTS.replace("{" + PARAM_CART_ID + "}", "1");

        mockMvc.perform(get(requestUri)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]." + FIELD_PRODUCT_CODE).value("P01"))
                .andExpect(jsonPath("$[0].quantity").value(2));
    }

    @Test
    void processOrder_Returns202_WhenSuccessful() throws Exception {
        String requestUri = (ENDPOINT_CARTS + SUB_ENDPOINT_PROCESS_ORDER)
                .replace("{" + PARAM_CART_ID + "}", "1");

        mockMvc.perform(post(requestUri)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getCart_Returns200_WhenSuccessful() throws Exception {
        when(cartService.getCartById(any())).thenReturn(cart);

        String requestUri = (ENDPOINT_CARTS + SUB_ENDPOINT_GET_CART)
                .replace("{" + PARAM_CART_ID + "}", "1");

        mockMvc.perform(get(requestUri)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }
}