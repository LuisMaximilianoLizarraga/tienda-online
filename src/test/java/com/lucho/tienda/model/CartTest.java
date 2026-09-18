package com.lucho.tienda.model;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CartTest {

    @Test
    void calculateTotal_ReturnsZero_WhenCartIsEmpty() {
        Cart cart = new Cart();
        cart.setItems(new HashSet<>() {
        });
        assertEquals(BigDecimal.ZERO, cart.calculateTotal());
    }

    @Test
    void calculateTotal_CalculatesCorrectly_WithValidItems() {
        Cart cart = new Cart();
        cart.setItems(new HashSet<>() {
        });
        Product p1 = new Product();
        p1.setPrice(new BigDecimal("100.00"));

        CartItem item1 = new CartItem();
        item1.setProduct(p1);
        item1.setQuantity(2); // 200.00

        cart.getItems().add(item1);

        assertEquals(new BigDecimal("200.00"), cart.calculateTotal());
    }

    @Test
    void calculateTotal_HandlesNullPriceAndQuantity_Safely() {
        Cart cart = new Cart();
        cart.setItems(new HashSet<>() {
        });
        Product p1 = new Product();
        p1.setPrice(null); // Null price!

        CartItem item1 = new CartItem();
        item1.setProduct(p1);
        item1.setQuantity(null); // Null quantity!

        cart.getItems().add(item1);

        // Should fallback to 0 * 0 without throwing NullPointerException
        assertEquals(BigDecimal.ZERO, cart.calculateTotal());
    }
    @Test
    void calculateTotal_ReturnsZero_WhenItemsListIsExplicitlyNull() {
        // Arrange: The list itself is null, not just empty
        Cart cart = new Cart();
        cart.setItems(null);

        // Act & Assert: Should hit the first 'if' branch and return ZERO
        assertEquals(BigDecimal.ZERO, cart.calculateTotal());
    }

    @Test
    void calculateTotal_IgnoresNullCartItems_Safely() {
        // Arrange
        Cart cart = new Cart();
        cart.setItems(new HashSet<>() {
        });
        Product p1 = new Product();
        p1.setPrice(new BigDecimal("100.00"));

        CartItem validItem = new CartItem();
        validItem.setProduct(p1);
        validItem.setQuantity(1);

        // Inject a valid item and a completely null object into the list
        cart.getItems().add(validItem);
        cart.getItems().add(null);

        // Act & Assert: The stream should filter out the null object without throwing NPE
        assertEquals(new BigDecimal("100.00"), cart.calculateTotal());
    }

    @Test
    void calculateTotal_IgnoresCartItemsWithNullProduct_Safely() {
        // Arrange: The CartItem exists, but the Product inside it is null
        Cart cart = new Cart();
        cart.setItems(new HashSet<>() {
        });
        CartItem itemWithoutProduct = new CartItem();
        itemWithoutProduct.setProduct(null);
        itemWithoutProduct.setQuantity(2);

        cart.getItems().add(itemWithoutProduct);

        // Act & Assert: The filter(item -> item.getProduct() != null) should catch this
        assertEquals(BigDecimal.ZERO, cart.calculateTotal());
    }

}