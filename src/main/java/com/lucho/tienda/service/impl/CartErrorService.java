package com.lucho.tienda.service.impl;

import com.lucho.tienda.model.enums.CartStatus;
import com.lucho.tienda.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartErrorService {

    private final CartRepository cartRepository;

    // REQUIRES_NEW suspends the failed transaction and opens a new one, ensuring the save is committed.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCartAsFailed(Long cartId) {
        try {
            cartRepository.findCartById(cartId).ifPresent(cart -> {
                cart.setStatus(CartStatus.FAILED);
                cartRepository.save(cart);
                log.warn("Marked cart ID {} as FAILED due to processing error.", cartId);
            });
        } catch (Exception ex) {
            log.error("Could not update cart ID {} status to FAILED", cartId, ex);
        }
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCartAsCancelled(Long cartId) {
        cartRepository.findById(cartId).ifPresent(cart -> {
            cart.setStatus(CartStatus.CANCELLED);
            cartRepository.save(cart);
            log.warn("Cart ID {} has been marked as CANCELLED due to out of stock.", cartId);
        });
    }
}