package com.lucho.tienda.service;

import com.lucho.tienda.dto.ProductOperationRequest;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public interface CartService {

    Cart createCart(Long userId);

    Cart addProduct(ProductOperationRequest request);

    Cart removeProduct(ProductOperationRequest request);

    List<CartItem> getCartProducts(Long cartId);

    List<Cart> getUserCarts(Long userId);

    Cart getCartById(Long cartId);

    void initiateCheckout(Long cartId);
}