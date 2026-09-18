package com.lucho.tienda.dto;

import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Category;
import com.lucho.tienda.model.Product;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CartItemResponseTest {

    // --- TESTS FOR fromEntity(CartItem item) ---

    @Test
    void fromEntity_ReturnsNull_WhenItemIsNull() {
        assertNull(CartItemResponse.fromEntity(null));
    }

    @Test
    void fromEntity_ReturnsValidResponse_WithFrozenUnitPriceAndDiscountAndPositiveQuantity() {
        Product product = new Product();
        product.setId(5L);
        product.setCode("P-100");
        product.setName("Creatine");
        product.setCategory(new Category());
        product.setPrice(new BigDecimal("150.00")); // Catalog price ignored since unitPrice is frozen

        CartItem item = new CartItem();
        item.setId(1L);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("200.00"));      // Frozen unit price
        item.setDiscountAmount(new BigDecimal("30.00"));  // Frozen discount
        item.setProduct(product);

        CartItemResponse response = CartItemResponse.fromEntity(item);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("P-100", response.productCode());
        assertEquals(2, response.quantity());
        assertEquals(new BigDecimal("200.00"), response.unitPrice());
        assertEquals(new BigDecimal("30.00"), response.discountAmount());
        assertEquals(new BigDecimal("170.00"), response.netUnitPrice()); // 200.00 - 30.00
        assertEquals(new BigDecimal("340.00"), response.lineTotal());    // 170.00 * 2
    }

    @Test
    void fromEntity_FallsBackToCatalogPriceAndZeroDiscount_WhenFrozenValuesAreNull() {
        Product product = new Product();
        product.setId(5L);
        product.setCode("P-200");
        product.setPrice(new BigDecimal("150.00"));

        CartItem item = new CartItem();
        item.setId(2L);
        item.setQuantity(3);
        item.setUnitPrice(null);        // Triggers fallback to product.getPrice()
        item.setDiscountAmount(null);   // Triggers fallback to BigDecimal.ZERO
        item.setProduct(product);

        CartItemResponse response = CartItemResponse.fromEntity(item);

        assertNotNull(response);
        assertEquals(2L, response.id());
        assertEquals("P-200", response.productCode());
        assertEquals(3, response.quantity());
        assertEquals(new BigDecimal("150.00"), response.unitPrice());
        assertEquals(BigDecimal.ZERO, response.discountAmount());
        assertEquals(new BigDecimal("150.00"), response.netUnitPrice()); // 150.00 - 0.00
        assertEquals(new BigDecimal("450.00"), response.lineTotal());    // 150.00 * 3
    }

    @Test
    void fromEntity_DefaultsQuantityToOne_WhenQuantityIsNull() {
        Product product = new Product();
        product.setCode("P-300");
        product.setPrice(new BigDecimal("100.00"));

        CartItem item = new CartItem();
        item.setId(3L);
        item.setQuantity(null); // Triggers fallback to quantity = 1
        item.setProduct(product);

        CartItemResponse response = CartItemResponse.fromEntity(item);

        assertNotNull(response);
        assertEquals(1, response.quantity());
        assertEquals(new BigDecimal("100.00"), response.lineTotal()); // 100.00 * 1
    }

    @Test
    void fromEntity_DefaultsQuantityToOne_WhenQuantityIsZeroOrNegative() {
        Product product = new Product();
        product.setCode("P-400");
        product.setPrice(new BigDecimal("100.00"));

        // Test with quantity = 0
        CartItem itemZeroQty = new CartItem();
        itemZeroQty.setId(4L);
        itemZeroQty.setQuantity(0); // Triggers fallback to quantity = 1
        itemZeroQty.setProduct(product);

        CartItemResponse responseZero = CartItemResponse.fromEntity(itemZeroQty);
        assertNotNull(responseZero);
        assertEquals(1, responseZero.quantity());

        // Test with negative quantity
        CartItem itemNegativeQty = new CartItem();
        itemNegativeQty.setId(5L);
        itemNegativeQty.setQuantity(-5); // Triggers fallback to quantity = 1
        itemNegativeQty.setProduct(product);

        CartItemResponse responseNegative = CartItemResponse.fromEntity(itemNegativeQty);
        assertNotNull(responseNegative);
        assertEquals(1, responseNegative.quantity());
    }

    @Test
    void fromEntity_ThrowsNPE_WhenProductIsNull() {
        CartItem item = new CartItem();
        item.setId(6L);
        item.setQuantity(1);
        item.setProduct(null);

        assertThrows(NullPointerException.class, () -> CartItemResponse.fromEntity(item));
    }

    @Test
    void fromEntity_HandlesNullProductFields_Safely() {
        Product product = new Product();
        product.setId(7L);
        product.setCode(null);
        product.setPrice(BigDecimal.TEN);

        CartItem item = new CartItem();
        item.setId(1L);
        item.setQuantity(2);
        item.setProduct(product);

        CartItemResponse response = CartItemResponse.fromEntity(item);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertNull(response.productCode());
        assertEquals(2, response.quantity());
    }

    // --- TESTS FOR fromEntityList(List<CartItem> items) ---

    @Test
    void fromEntityList_ReturnsEmptyList_WhenListIsNull() {
        List<CartItemResponse> responses = CartItemResponse.fromEntityList(null);
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void fromEntityList_ReturnsEmptyList_WhenListIsEmpty() {
        List<CartItemResponse> responses = CartItemResponse.fromEntityList(List.of());
        assertNotNull(responses);
        assertTrue(responses.isEmpty());
    }

    @Test
    void fromEntityList_ReturnsMappedList_WhenListHasValidItems() {
        Product p1 = new Product(); p1.setCode("P-1"); p1.setPrice(BigDecimal.TEN);
        CartItem item1 = new CartItem(); item1.setId(10L); item1.setProduct(p1);

        Product p2 = new Product(); p2.setCode("P-2"); p2.setPrice(BigDecimal.ONE);
        CartItem item2 = new CartItem(); item2.setId(20L); item2.setProduct(p2);

        List<CartItemResponse> responses = CartItemResponse.fromEntityList(List.of(item1, item2));

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(10L, responses.get(0).id());
        assertEquals("P-1", responses.get(0).productCode());
        assertEquals(20L, responses.get(1).id());
        assertEquals("P-2", responses.get(1).productCode());
    }

    @Test
    void fromEntityList_HandlesListWithNullElements_Safely() {
        Product p1 = new Product(); p1.setCode("P-1"); p1.setPrice(BigDecimal.TEN);
        CartItem item1 = new CartItem(); item1.setId(10L); item1.setProduct(p1);

        List<CartItem> itemsWithNull = new ArrayList<>();
        itemsWithNull.add(item1);
        itemsWithNull.add(null); // Contains a null CartItem element

        List<CartItemResponse> responses = CartItemResponse.fromEntityList(itemsWithNull);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertNotNull(responses.get(0));
        assertNull(responses.get(1)); // Second item mapped to null
    }
}