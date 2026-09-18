package com.lucho.tienda.service.impl;

import com.lucho.tienda.dto.CartItemResponse;
import com.lucho.tienda.dto.ProductOperationRequest;
import com.lucho.tienda.exception.BadRequestException;
import com.lucho.tienda.exception.ResourceNotFoundException;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Product;
import com.lucho.tienda.model.User;
import com.lucho.tienda.model.enums.CartStatus;
import com.lucho.tienda.repository.CartRepository;
import com.lucho.tienda.repository.ProductRepository;
import com.lucho.tienda.repository.UserRepository;
import com.lucho.tienda.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static com.lucho.tienda.constant.ErrorMessageConstants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final OrderProcessingWorker orderProcessingWorker;

    @Override
    @Transactional
    public Cart createCart(Long userId) {
        if (userId == null) {
            throw new BadRequestException(USER_ID_REQUIRED);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(USER_NOT_FOUND, userId)));

        Cart cart = Cart.builder()
                .user(user)
                .items(new HashSet<>())
                .build();
        return cartRepository.save(cart);
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${cart.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${cart.retry.backoff-delay:100}")
    )
    public Cart addProduct(ProductOperationRequest request) {
        validateProductRequest(request);

        Cart cart = getCartWithProducts(request.cartId());
        // 1. Validates that the cart is in CREATED status before modifying items
        if (cart.getStatus().cannotModify()) {
            throw new BadRequestException(String.format(CANNOT_MODIFY_CART_STATUS, cart.getStatus()));
        }

        Product product = getProduct(request.productCode());

        // 2. Check if the product is already in the cart to determine the current quantity
        Optional<CartItem> existingItemOpt = cart.getItems().stream()
                .filter(item -> item.getProduct().getCode().equals(product.getCode()))
                .findFirst();

        int currentQuantity = existingItemOpt.map(CartItem::getQuantity).orElse(0);
        int requestedQuantity = currentQuantity + 1;

        // 3. Defensive Validation: Check if we have enough stock available
        if (product.getStock() < requestedQuantity) {
            throw new BadRequestException(String.format(OUT_OF_STOCK, product.getCode()));
        }

        // 4. Update existing item or add a new one (live catalog pricing while status is CREATED)
        existingItemOpt.ifPresentOrElse(
                item -> item.setQuantity(requestedQuantity),
                () -> {
                    CartItem newItem = CartItem.builder()
                            .cart(cart)
                            .product(product)
                            .quantity(requestedQuantity)
                            .build();
                    cart.getItems().add(newItem);
                }
        );

        // 5. Update the cart total to force the @Version increment (Optimistic Locking)
        return saveCartWithUpdatedTotal(cart);
    }

    @Override
    @Transactional
    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttemptsExpression = "${cart.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${cart.retry.backoff-delay:100}")
    )
    public Cart removeProduct(ProductOperationRequest request) {
        validateProductRequest(request);
        Cart cart = getCartWithProducts(request.cartId());

        // Validates that the cart is in CREATED status before modifying items
        if (cart.getStatus().cannotModify()) {
            throw new BadRequestException(String.format(CANNOT_MODIFY_CART_STATUS, cart.getStatus()));
        }

        // Find the item or throw an exception if it doesn't exist in the cart
        CartItem itemToRemove = cart.getItems().stream()
                .filter(item -> item.getProduct().getCode().equals(request.productCode()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(PRODUCT_NOT_FOUND, request.productCode())));

        // Decrease quantity or remove the item entirely
        if (itemToRemove.getQuantity() > 1) {
            itemToRemove.setQuantity(itemToRemove.getQuantity() - 1);
        } else {
            cart.getItems().remove(itemToRemove);
        }

        // Update the cart total to force the @Version increment (Optimistic Locking)
        return saveCartWithUpdatedTotal(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CartItem> getCartProducts(Long cartId) {
        if (cartId == null) {
            throw new BadRequestException(CART_ID_REQUIRED);
        }

        Cart cart = getCartWithProducts(cartId);
        // Convert the Set to a List ordered by the item's ID
        // to ensure predictable ordering for the frontend.
        return cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Cart> getUserCarts(Long userId) {
        if (userId == null) {
            throw new BadRequestException(USER_ID_REQUIRED);
        }
        return cartRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public void initiateCheckout(Long cartId) {
        // 1. Fetch the cart to let Hibernate track its current @Version
        Cart cart = cartRepository.findCartById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        String.format(CART_NOT_FOUND, cartId)));

        // 2. State Pattern: Let the Rich Enum validate if checkout is allowed (Zero 'if' statements)
        cart.getStatus().validateForCheckout();

        // 3. Lock the cart synchronously.
        // Saving it triggers Hibernate's @Version check, preventing double-click race conditions.
        cart.setStatus(CartStatus.PROCESSING);
        cartRepository.save(cart);

        // 4. Delegate heavy price freezing and discount processing to the async worker
        orderProcessingWorker.processOrderAsync(cartId);
    }

    @Override
    @Transactional(readOnly = true)
    public Cart getCartById(Long cartId) {
        if (cartId == null) {
            throw new BadRequestException(CART_ID_REQUIRED);
        }
        return getCartWithProducts(cartId);
    }

    private Cart getCartWithProducts(Long cartId) {
        return cartRepository.findCartById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(CART_NOT_FOUND, cartId)));
    }

    private Product getProduct(String code) {
        return productRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(String.format(PRODUCT_NOT_FOUND, code)));
    }

    private void validateProductRequest(ProductOperationRequest request) {
        if (request == null || request.cartId() == null || request.productCode() == null || request.productCode().isBlank()) {
            throw new BadRequestException(INVALID_PRODUCT_REQUEST);
        }
    }

    /**
     * Validates that the cart is in a state that allows modifications.
     */
    private void validateCartModifiable(Cart cart) {
        if (cart.getStatus().cannotModify()) {
            throw new BadRequestException(String.format(CANNOT_MODIFY_CART_STATUS, cart.getStatus()));
        }
    }

    /**
     * Recalculates the cart total (triggering @Version increment) and saves it.
     */
    private Cart saveCartWithUpdatedTotal(Cart cart) {
        cart.setTotalAmount(cart.calculateTotal());
        return cartRepository.save(cart);
    }
}