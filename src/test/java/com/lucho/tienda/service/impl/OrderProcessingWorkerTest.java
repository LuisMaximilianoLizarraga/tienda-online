package com.lucho.tienda.service.impl;

import com.lucho.tienda.exception.BadRequestException;
import com.lucho.tienda.exception.OutOfStockException;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Product;
import com.lucho.tienda.model.enums.CartStatus;
import com.lucho.tienda.repository.CartRepository;
import com.lucho.tienda.service.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessingWorkerTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartErrorService cartErrorService;

    @Mock
    private StockService stockService;

    @InjectMocks
    private OrderProcessingWorker worker;

    private Cart cart;

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setCode("P01");
        product.setPrice(new BigDecimal("1000.00"));

        cart = new Cart();
        cart.setId(1L);
        cart.setStatus(CartStatus.PROCESSING);
        cart.setItems(new HashSet<>());

        CartItem item = new CartItem();
        item.setProduct(product);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("1000.00"));
        item.setDiscountAmount(BigDecimal.ZERO);
        cart.getItems().add(item);

        ReflectionTestUtils.setField(worker, "sleepMs", 0L);
    }

    @Test
    void processOrderAsync_Success_UpdatesToProcessed() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        doNothing().when(stockService).deductStockForCart(any());

        worker.processOrderAsync(1L);

        assertEquals(CartStatus.PROCESSED, cart.getStatus());
        assertEquals(new BigDecimal("1000.00"), cart.getTotalAmount());
        verify(stockService).deductStockForCart(cart);
        verify(cartRepository).save(cart);
    }

    @Test
    void processOrderAsync_SkipsExecution_WhenStatusIsNotProcessing() {
        cart.setStatus(CartStatus.CREATED);
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));

        worker.processOrderAsync(1L);

        verify(stockService, never()).deductStockForCart(any());
        verify(cartRepository, never()).save(any());
    }

    @Test
    void processOrderAsync_Fails_UpdatesToCancelled_WhenOutOfStock() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        doThrow(new OutOfStockException("P01")).when(stockService).deductStockForCart(cart);

        worker.processOrderAsync(1L);

        verify(cartErrorService).markCartAsCancelled(1L);
        verify(cartRepository, never()).save(cart);
    }

    @Test
    void processOrderAsync_SwallowsOptimisticLockingFailure_Gracefully() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        doNothing().when(stockService).deductStockForCart(cart);
        when(cartRepository.save(cart)).thenThrow(new ObjectOptimisticLockingFailureException("Cart", 1L));

        worker.processOrderAsync(1L);

        verify(cartErrorService, never()).markCartAsFailed(any());
    }

    @Test
    void processOrderAsync_HandlesInterruptedException_Safely() {
        ReflectionTestUtils.setField(worker, "sleepMs", 1000L);
        Thread.currentThread().interrupt();

        worker.processOrderAsync(1L);

        verify(cartErrorService).markCartAsFailed(1L);
        Thread.interrupted(); // Clear flag
    }

    @Test
    void processOrderAsync_Fails_UpdatesToFailed_OnUnexpectedDatabaseError() {
        when(cartRepository.findCartById(1L)).thenReturn(Optional.of(cart));
        doThrow(new RuntimeException("DB down")).when(stockService).deductStockForCart(cart);

        assertThrows(RuntimeException.class, () -> worker.processOrderAsync(1L));

        verify(cartErrorService).markCartAsFailed(1L);
    }
}