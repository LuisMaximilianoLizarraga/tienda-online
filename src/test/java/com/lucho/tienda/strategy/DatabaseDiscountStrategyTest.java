package com.lucho.tienda.strategy;

import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Category;
import com.lucho.tienda.model.Discount;
import com.lucho.tienda.model.Product;
import com.lucho.tienda.repository.DiscountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseDiscountStrategyTest {

    @Mock
    private DiscountRepository discountRepository;

    @InjectMocks
    private DatabaseDiscountStrategy discountStrategy;

    private Cart cart;
    private Category mainCategory;

    @BeforeEach
    void setUp() {
        cart = new Cart();
        cart.setItems(new HashSet<>());

        mainCategory = new Category();
        mainCategory.setId(1L);
        mainCategory.setName("Proteinas");

        Product product = new Product();
        product.setCode("P01");
        product.setCategory(mainCategory);
        product.setPrice(new BigDecimal("1000.00"));

        CartItem cartItem = new CartItem();
        cartItem.setProduct(product);
        cartItem.setQuantity(2); // 2 units = 200.00 total discount at 10%

        cart.getItems().add(cartItem);
    }

    // --- TESTS FOR calculateDiscount(Cart cart) ---

    @Test
    void calculateDiscount_AppliesMultipliedDiscount_WhenCategoryMatches() {
        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(discount));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("200.00"), result);
    }

    @Test
    void calculateDiscount_ReturnsZero_WhenNoActiveDiscounts() {
        when(discountRepository.findByActiveTrue()).thenReturn(List.of());
        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void calculateDiscount_ReturnsZero_WhenCartIsNull() {
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateDiscount(null));
    }

    @Test
    void calculateDiscount_ReturnsZero_WhenCartItemsAreNullOrEmpty() {
        cart.setItems(null);
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateDiscount(cart));

        cart.setItems(new HashSet<>());
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateDiscount(cart));
    }

    @Test
    void calculateDiscount_UsesFrozenUnitPrice_WhenAvailable() {
        CartItem item = cart.getItems().iterator().next();
        item.setUnitPrice(new BigDecimal("500.00")); // Frozen price lower than catalog (1000.00)

        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(discount));

        // 500.00 * 2 units * 10% = 100.00
        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void calculateDiscount_SkipsInvalidItems_Safely() {
        CartItem badItemProductNull = new CartItem();
        badItemProductNull.setProduct(null);
        cart.getItems().add(badItemProductNull);

        cart.getItems().add(null);

        CartItem badItemCategoryNull = new CartItem();
        Product pWithoutCategory = new Product();
        pWithoutCategory.setPrice(new BigDecimal("100.00"));
        badItemCategoryNull.setProduct(pWithoutCategory);
        cart.getItems().add(badItemCategoryNull);

        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(discount));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("200.00"), result);
    }

    @Test
    void calculateDiscount_HandlesNullOrInvalidQuantity_Safely() {
        // Test null quantity (defaults to 1)
        cart.getItems().iterator().next().setQuantity(null);

        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(discount));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("100.00"), result);

        // Test zero or negative quantity (defaults to 1)
        cart.getItems().iterator().next().setQuantity(-5);
        result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("100.00"), result);
    }

    @Test
    void calculateDiscount_ReturnsZero_WhenBasePriceIsNullOrZeroOrNegative() {
        Product p = cart.getItems().iterator().next().getProduct();
        p.setPrice(null);

        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(discount));

        assertEquals(BigDecimal.ZERO, discountStrategy.calculateDiscount(cart));

        p.setPrice(BigDecimal.ZERO);
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateDiscount(cart));

        p.setPrice(new BigDecimal("-50.00"));
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateDiscount(cart));
    }

    @Test
    void calculateDiscount_IgnoresDiscounts_WithNullCategoryOrPercentage() {
        Discount d1 = new Discount();
        d1.setCategory(mainCategory);
        d1.setPercentage(null); // Invalid percentage
        d1.setActive(true);

        Discount d2 = new Discount();
        d2.setCategory(null); // Invalid category
        d2.setPercentage(new BigDecimal("10.00"));
        d2.setActive(true);

        Discount d3 = new Discount();
        Category catWithoutId = new Category(); // ID is null
        d3.setCategory(catWithoutId);
        d3.setPercentage(new BigDecimal("10.00"));
        d3.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(d1, d2, d3));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void calculateDiscount_IgnoresDiscounts_WithZeroOrNegativePercentageOrInactive() {
        Discount d1 = new Discount();
        d1.setCategory(mainCategory);
        d1.setPercentage(BigDecimal.ZERO);
        d1.setActive(true);

        Discount d2 = new Discount();
        d2.setCategory(mainCategory);
        d2.setPercentage(new BigDecimal("-10.00"));
        d2.setActive(true);

        Discount d3 = new Discount();
        d3.setCategory(mainCategory);
        d3.setPercentage(new BigDecimal("10.00"));
        d3.setActive(false); // Inactive

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(d1, d2, d3));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void calculateDiscount_HandlesDuplicateCategoriesInDiscounts_ResolvesFirst() {
        Discount d1 = new Discount();
        d1.setCategory(mainCategory);
        d1.setPercentage(new BigDecimal("10.00"));
        d1.setActive(true);

        Discount d2 = new Discount();
        d2.setCategory(mainCategory);
        d2.setPercentage(new BigDecimal("20.00"));
        d2.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(d1, d2));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("200.00"), result); // Keeps first percentage (10%)
    }

    @Test
    void calculateDiscount_ReturnsZero_WhenCategoryDoesNotMatchAnyDiscount() {
        Category differentCategory = new Category();
        differentCategory.setId(99L);

        Discount discount = new Discount();
        discount.setCategory(differentCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(discount));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(BigDecimal.ZERO, result);
    }

    @Test
    void calculateDiscount_AppliesMultipleDiscounts_ForDifferentCategories() {
        Category cat2 = new Category();
        cat2.setId(2L);

        Product p2 = new Product();
        p2.setCode("V01");
        p2.setCategory(cat2);
        p2.setPrice(new BigDecimal("500.00"));

        CartItem item2 = new CartItem();
        item2.setProduct(p2);
        item2.setQuantity(1);
        cart.getItems().add(item2);

        Discount d1 = new Discount();
        d1.setCategory(mainCategory);
        d1.setPercentage(new BigDecimal("10.00"));
        d1.setActive(true);

        Discount d2 = new Discount();
        d2.setCategory(cat2);
        d2.setPercentage(new BigDecimal("20.00"));
        d2.setActive(true);

        when(discountRepository.findByActiveTrue()).thenReturn(List.of(d1, d2));

        BigDecimal result = discountStrategy.calculateDiscount(cart);
        assertEquals(new BigDecimal("300.00"), result); // 200 + 100
    }

    // --- TESTS FOR calculateItemDiscount(CartItem item, List<Discount> activeDiscounts) ---

    @Test
    void calculateItemDiscount_ReturnsZero_WhenItemProductOrCategoryIsNull() {
        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);
        List<Discount> discounts = List.of(discount);

        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(null, discounts));

        CartItem itemNullProduct = new CartItem();
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(itemNullProduct, discounts));

        CartItem itemNullCategory = new CartItem();
        itemNullCategory.setProduct(new Product());
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(itemNullCategory, discounts));
    }

    @Test
    void calculateItemDiscount_ReturnsZero_WhenDiscountsListNullOrEmpty() {
        CartItem item = cart.getItems().iterator().next();

        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(item, null));
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(item, List.of()));
    }

    @Test
    void calculateItemDiscount_ReturnsZero_WhenBasePriceInvalid() {
        CartItem item = cart.getItems().iterator().next();
        item.getProduct().setPrice(null);

        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(item, List.of(discount)));

        item.getProduct().setPrice(new BigDecimal("-10.00"));
        assertEquals(BigDecimal.ZERO, discountStrategy.calculateItemDiscount(item, List.of(discount)));
    }

    @Test
    void calculateItemDiscount_AppliesDiscount_WithFrozenUnitPriceAndDefaultQuantity() {
        CartItem item = cart.getItems().iterator().next();
        item.setUnitPrice(new BigDecimal("500.00"));
        item.setQuantity(null); // Defaults to 1

        Discount discount = new Discount();
        discount.setCategory(mainCategory);
        discount.setPercentage(new BigDecimal("10.00"));
        discount.setActive(true);

        // 500.00 * 1 unit * 10% = 50.00
        BigDecimal result = discountStrategy.calculateItemDiscount(item, List.of(discount));
        assertEquals(new BigDecimal("50.00"), result);
    }

    @Test
    void calculateItemDiscount_FiltersOutInvalidOrNonMatchingDiscounts() {
        CartItem item = cart.getItems().iterator().next();

        Discount inactive = new Discount();
        inactive.setCategory(mainCategory);
        inactive.setPercentage(new BigDecimal("10.00"));
        inactive.setActive(false);

        Discount nullCategory = new Discount();
        nullCategory.setCategory(null);
        nullCategory.setPercentage(new BigDecimal("10.00"));
        nullCategory.setActive(true);

        Discount differentCategory = new Discount();
        Category catDiff = new Category();
        catDiff.setId(99L);
        differentCategory.setCategory(catDiff);
        differentCategory.setPercentage(new BigDecimal("10.00"));
        differentCategory.setActive(true);

        Discount nullOrZeroPercentage = new Discount();
        nullOrZeroPercentage.setCategory(mainCategory);
        nullOrZeroPercentage.setPercentage(BigDecimal.ZERO);
        nullOrZeroPercentage.setActive(true);

        Discount valid = new Discount();
        valid.setCategory(mainCategory);
        valid.setPercentage(new BigDecimal("15.00"));
        valid.setActive(true);

        List<Discount> list = List.of(inactive, nullCategory, differentCategory, nullOrZeroPercentage, valid);

        // 1000.00 * 2 units * 15% = 300.00
        BigDecimal result = discountStrategy.calculateItemDiscount(item, list);
        assertEquals(new BigDecimal("300.00"), result);
    }
}