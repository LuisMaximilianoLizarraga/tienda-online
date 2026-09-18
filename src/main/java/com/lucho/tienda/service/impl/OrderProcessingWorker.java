package com.lucho.tienda.service.impl;

import com.lucho.tienda.constant.ErrorMessageConstants;
import com.lucho.tienda.exception.OutOfStockException;
import com.lucho.tienda.exception.ResourceNotFoundException;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.enums.CartStatus;
import com.lucho.tienda.repository.CartRepository;
import com.lucho.tienda.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.StaleObjectStateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderProcessingWorker {

    private final CartRepository cartRepository;
    private final CartErrorService cartErrorService;
    private final StockService stockService;

    @Value("${cart.processing.sleep-ms:0}")
    private long sleepMs;

    @Async("asyncExecutor")
    @Transactional(noRollbackFor = OutOfStockException.class)
    public void processOrderAsync(Long cartId) {
        log.info("Starting asynchronous order processing for cart ID: {}", cartId);

        try {
            if (sleepMs > 0) {
                Thread.sleep(sleepMs);
            }

            Cart cart = cartRepository.findCartById(cartId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            String.format(ErrorMessageConstants.CART_NOT_FOUND, cartId)));

            if (cart.getStatus() != CartStatus.PROCESSING) {
                log.warn("Async execution skipped. Cart ID {} is in state {} instead of PROCESSING.", cartId, cart.getStatus());
                return;
            }
            // 1. Delegate atomic stock deduction and price/discount freezing
            stockService.deductStockForCart(cart);

            // 2. Calculate accumulated total amount using CartItem business logic
            BigDecimal accumulatedTotal = cart.getItems().stream()
                    .map(CartItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // 3. Update total amount and set cart status to PROCESSED
            cart.setTotalAmount(accumulatedTotal);
            cart.setStatus(CartStatus.PROCESSED);

            cartRepository.save(cart);
            log.info("Successfully processed order for cart ID: {}", cartId);

        } catch (OutOfStockException e) {
            log.warn("Order processing cancelled for cart ID: {}. Reason: {}", cartId, e.getMessage());
            cartErrorService.markCartAsCancelled(cartId);
        } catch (ObjectOptimisticLockingFailureException | StaleObjectStateException e) {
            // Silently swallows version conflicts
            log.warn("Concurrent modification detected for cart ID: {}. Another thread processed it.", cartId);
        }catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Asynchronous processing interrupted for cart ID: {}", cartId, e);
            cartErrorService.markCartAsFailed(cartId);
        } catch (Exception e) {
            log.error("Error occurred while processing async order for cart ID: {}", cartId, e);
            cartErrorService.markCartAsFailed(cartId);
            throw e;
        }
    }
}