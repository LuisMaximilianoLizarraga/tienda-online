package com.lucho.tienda.service.impl;

import com.lucho.tienda.dto.ProductOperationRequest;
import com.lucho.tienda.exception.BadRequestException;
import com.lucho.tienda.exception.ResourceNotFoundException;
import com.lucho.tienda.model.*;
import com.lucho.tienda.model.enums.CartStatus;
import com.lucho.tienda.repository.CartRepository;
import com.lucho.tienda.repository.ProductRepository;
import com.lucho.tienda.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderProcessingWorker orderProcessingWorker;

    @InjectMocks
    private CartServiceImpl cartService;

    private User user;
    private Cart cart;
    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);

        product = new Product();
        product.setId(1L);
        product.setCode("P01");
        product.setName("Whey Protein");
        product.setPrice(new BigDecimal("1000.00"));
        product.setCategory(new Category());
        product.setStock(10);

        cart = new Cart();
        cart.setId(1L);
        cart.setUser(user);
        cart.setStatus(CartStatus.CREATED); // Default valid state
        cart.setItems(new HashSet<>() {
        });
        cartItem = new CartItem();
        cartItem.setId(1L);
        cartItem.setCart(cart);
        cartItem.setProduct(product);
        cartItem.setQuantity(1);

        cart.getItems().add(cartItem);

        lenient().when(cartRepository.save(any(Cart.class))).thenReturn(cart);
        lenient().when(cartRepository.save(any(Cart.class))).thenReturn(cart);
    }

    // --- CREATE CART ---

    @Test
    void createCart_Success_WhenUserExists() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        Cart created = cartService.createCart(1L);

        assertNotNull(created);
        assertEquals(1L, created.getUser().getId());
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void createCart_ThrowsBadRequest_WhenUserIdIsNull() {
        assertThrows(BadRequestException.class, () -> cartService.createCart(null));
    }

    @Test
    void createCart_ThrowsNotFound_WhenUserDoesNotExist() {
        when(userRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cartService.createCart(1L));
    }

    // --- ADD PRODUCT ---

    @Test
    void addProduct_IncreasesQuantity_WhenProductAlreadyInCart() {
        ProductOperationRequest request = new ProductOperationRequest(1L, "P01");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findByCode("P01")).thenReturn(Optional.of(product));

        Cart updatedCart = cartService.addProduct(request);

        assertEquals(1, updatedCart.getItems().size()); // El tamaño de la lista sigue siendo 1 (mismo producto)

        assertEquals(2, updatedCart.getItems().stream().findFirst().orElseThrow().getQuantity());

        verify(cartRepository).save(cart);
    }

    @Test
    void addProduct_AddNewItem_WhenProductNotInCart() {
        Product newProduct = new Product();
        newProduct.setCode("P02");
        newProduct.setPrice(new BigDecimal("500.00"));
        newProduct.setStock(10);

        ProductOperationRequest request = new ProductOperationRequest(1L, "P02");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findByCode("P02")).thenReturn(Optional.of(newProduct));

        Cart updatedCart = cartService.addProduct(request);

        assertEquals(2, updatedCart.getItems().size());
        verify(cartRepository).save(cart);
    }

    @Test
    void addProduct_ThrowsBadRequest_OnInvalidRequest() {
        assertThrows(BadRequestException.class, () -> cartService.addProduct(null));
        assertThrows(BadRequestException.class, () -> cartService.addProduct(new ProductOperationRequest(null, "P01")));
        assertThrows(BadRequestException.class, () -> cartService.addProduct(new ProductOperationRequest(1L, null)));
        assertThrows(BadRequestException.class, () -> cartService.addProduct(new ProductOperationRequest(1L, "   ")));
    }

    @Test
    void addProduct_ThrowsNotFound_WhenProductDoesNotExist() {
        ProductOperationRequest request = new ProductOperationRequest(1L, "P99");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findByCode("P99")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.addProduct(request));
    }

    @Test
    void addProduct_ThrowsNotFound_WhenCartDoesNotExist() {
        ProductOperationRequest request = new ProductOperationRequest(99L, "P01");
        when(cartRepository.findCartById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.addProduct(request));
    }

    // --- REMOVE PRODUCT ---

    @Test
    void removeProduct_DecreasesQuantity_WhenQuantityGreaterThanOne() {
        cartItem.setQuantity(2);
        ProductOperationRequest request = new ProductOperationRequest(1L, "P01");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        Cart updatedCart = cartService.removeProduct(request);

        assertEquals(1, updatedCart.getItems().size());
        assertEquals(1, updatedCart.getItems().stream().findFirst().orElseThrow().getQuantity());
        verify(cartRepository).save(cart);
    }

    @Test
    void removeProduct_RemovesItem_WhenQuantityIsOne() {
        ProductOperationRequest request = new ProductOperationRequest(1L, "P01");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        Cart updatedCart = cartService.removeProduct(request);

        assertTrue(updatedCart.getItems().isEmpty());
        verify(cartRepository).save(cart);
    }

    @Test
    void removeProduct_ThrowsNotFound_WhenProductNotInCart() {
        ProductOperationRequest request = new ProductOperationRequest(1L, "NON-EXISTENT");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        assertThrows(ResourceNotFoundException.class, () -> cartService.removeProduct(request));
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void removeProduct_ThrowsNotFound_WhenCartDoesNotExist() {
        ProductOperationRequest request = new ProductOperationRequest(99L, "P01");
        when(cartRepository.findCartById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.removeProduct(request));
    }

    // --- GETTERS ---

    @Test
    void getCartProducts_ReturnsCartItems() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        List<CartItem> items = cartService.getCartProducts(1L);
        assertFalse(items.isEmpty());
    }

    @Test
    void getCartProducts_ThrowsBadRequest_WhenIdNull() {
        assertThrows(BadRequestException.class, () -> cartService.getCartProducts(null));
    }

    @Test
    void getCartProducts_ThrowsNotFound_WhenCartDoesNotExist() {
        when(cartRepository.findCartById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cartService.getCartProducts(99L));
    }

    @Test
    void getUserCarts_Success() {
        when(cartRepository.findByUserId(1L)).thenReturn(List.of(cart));
        List<Cart> carts = cartService.getUserCarts(1L);
        assertEquals(1, carts.size());
    }

    @Test
    void getUserCarts_ReturnsEmptyList_WhenNoCartsFound() {
        when(cartRepository.findByUserId(2L)).thenReturn(List.of());
        List<Cart> carts = cartService.getUserCarts(2L);
        assertTrue(carts.isEmpty());
    }

    @Test
    void getUserCarts_ThrowsBadRequest_WhenIdNull() {
        assertThrows(BadRequestException.class, () -> cartService.getUserCarts(null));
    }

    @Test
    void getCartById_Success() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        Cart found = cartService.getCartById(1L);
        assertNotNull(found);
    }

    @Test
    void getCartById_ThrowsBadRequest_WhenIdNull() {
        assertThrows(BadRequestException.class, () -> cartService.getCartById(null));
    }

    @Test
    void getCartById_ThrowsNotFound_WhenCartDoesNotExist() {
        when(cartRepository.findCartById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cartService.getCartById(99L));
    }

    // --- ORCHESTRATION & CHECKOUT ---

    @Test
    void initiateCheckout_Success_LocksCartAndCallsWorker() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        cartService.initiateCheckout(1L);

        // Verify status changed to PROCESSING
        assertEquals(CartStatus.PROCESSING, cart.getStatus());

        // Verify it was saved to trigger Hibernate's @Version check
        verify(cartRepository).save(cart);

        // Verify worker was called
        verify(orderProcessingWorker).processOrderAsync(1L);
    }

    @Test
    void initiateCheckout_ThrowsBadRequest_WhenCartAlreadyProcessing() {
        cart.setStatus(CartStatus.PROCESSING); // Invalid state
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        assertThrows(BadRequestException.class, () -> cartService.initiateCheckout(1L));

        // Worker should never be called
        verify(orderProcessingWorker, never()).processOrderAsync(anyLong());
    }

    @Test
    void initiateCheckout_ThrowsNotFound_WhenCartDoesNotExist() {
        when(cartRepository.findCartById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.initiateCheckout(99L));
    }

    @Test
    void addProduct_ThrowsBadRequest_WhenInsufficientStock() {
        // Arrange: Product has 1 item in stock, and the cart already has 1.
        // Adding another one exceeds the available stock.
        product.setStock(1);
        ProductOperationRequest request = new ProductOperationRequest(1L, "P01");
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findByCode("P01")).thenReturn(Optional.of(product));

        // Act & Assert
        assertThrows(BadRequestException.class, () -> cartService.addProduct(request));

        // Verify that the cart is never saved if validation fails
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void removeProduct_ThrowsBadRequest_OnInvalidRequest() {
        // Act & Assert: Validates all null constraint branches
        assertThrows(BadRequestException.class, () -> cartService.removeProduct(null));
        assertThrows(BadRequestException.class, () -> cartService.removeProduct(new ProductOperationRequest(null, "P01")));
        assertThrows(BadRequestException.class, () -> cartService.removeProduct(new ProductOperationRequest(1L, null)));
        assertThrows(BadRequestException.class, () -> cartService.removeProduct(new ProductOperationRequest(1L, "   ")));
    }

    @Test
    void initiateCheckout_ThrowsBadRequest_WhenCartIsAlreadyProcessed() {
        // Arrange: Target the PROCESSED branch in the CartStatus enum validation
        cart.setStatus(CartStatus.PROCESSED);
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        // Act & Assert
        assertThrows(BadRequestException.class, () -> cartService.initiateCheckout(1L));

        // Worker must not be called if state is invalid
        verify(orderProcessingWorker, never()).processOrderAsync(anyLong());
    }
}