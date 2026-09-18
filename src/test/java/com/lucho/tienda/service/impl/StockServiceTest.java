package com.lucho.tienda.service.impl;

import com.lucho.tienda.exception.BadRequestException;
import com.lucho.tienda.exception.OutOfStockException;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Product;
import com.lucho.tienda.repository.DiscountRepository;
import com.lucho.tienda.repository.ProductRepository;
import com.lucho.tienda.service.StockService;
import com.lucho.tienda.strategy.DatabaseDiscountStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private DiscountRepository discountRepository;

    @Mock
    private DatabaseDiscountStrategy discountStrategy;

    @InjectMocks
    private StockService stockService;

    private Cart cart;
    private Product product;
    private CartItem item;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setCode("P01");
        product.setPrice(new BigDecimal("100.00"));

        item = new CartItem();
        item.setProduct(product);
        item.setQuantity(2);

        Set<CartItem> items = new HashSet<>();
        items.add(item);

        cart = new Cart();
        cart.setId(1L);
        cart.setItems(items);
    }

    @Test
    void deductStockForCart_Success() {
        when(discountRepository.findByActiveTrue()).thenReturn(new ArrayList<>());
        when(productRepository.decrementStockSafely("P01", 2)).thenReturn(1);
        when(discountStrategy.calculateItemDiscount(any(), any())).thenReturn(new BigDecimal("10.00"));

        stockService.deductStockForCart(cart);

        assertEquals(new BigDecimal("100.00"), item.getUnitPrice());
        assertEquals(new BigDecimal("10.00"), item.getDiscountAmount());
        verify(productRepository).decrementStockSafely("P01", 2);
    }

    @Test
    void deductStockForCart_ThrowsBadRequest_WhenCartIsNull() {
        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> stockService.deductStockForCart(null)
        );

        assertEquals("Cannot process stock for an empty cart.", exception.getMessage());
    }

    @Test
    void deductStockForCart_ThrowsBadRequest_WhenCartItemsAreEmpty() {
        cart.setItems(new HashSet<>());

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> stockService.deductStockForCart(cart)
        );

        assertEquals("Cannot process stock for an empty cart.", exception.getMessage());
    }

    @Test
    void deductStockForCart_ThrowsBadRequest_WhenStockIsInsufficient() {
        when(discountRepository.findByActiveTrue()).thenReturn(new ArrayList<>());
        when(productRepository.decrementStockSafely("P01", 2)).thenReturn(0); // 0 filas actualizadas

        assertThrows(OutOfStockException.class, () -> stockService.deductStockForCart(cart));
        verify(productRepository).decrementStockSafely("P01", 2);
    }

    @Test
    void deductStockForCart_UsesDefaultQuantity_WhenQuantityIsInvalid() {
        item.setQuantity(0);

        when(discountRepository.findByActiveTrue()).thenReturn(new ArrayList<>());
        when(productRepository.decrementStockSafely("P01", 1)).thenReturn(1);
        when(discountStrategy.calculateItemDiscount(any(), any())).thenReturn(BigDecimal.ZERO);

        stockService.deductStockForCart(cart);

        verify(productRepository).decrementStockSafely("P01", 1);
    }
}