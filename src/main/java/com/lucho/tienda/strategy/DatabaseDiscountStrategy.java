package com.lucho.tienda.strategy;

import com.lucho.tienda.constant.CartConstants;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Discount;
import com.lucho.tienda.repository.DiscountRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class DatabaseDiscountStrategy {

    private final DiscountRepository discountRepository;

    public DatabaseDiscountStrategy(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    public BigDecimal calculateDiscount(Cart cart) {
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<Discount> activeDiscounts = discountRepository.findByActiveTrue();
        if (activeDiscounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // Index active discounts by Category ID for O(1) lookup
        Map<Long, BigDecimal> discountMap = activeDiscounts.stream()
                .filter(Discount::getActive)
                .filter(d -> d.getCategory() != null && d.getCategory().getId() != null)
                .filter(d -> d.getPercentage() != null && d.getPercentage().compareTo(BigDecimal.ZERO) > 0)
                .collect(Collectors.toMap(
                        d -> d.getCategory().getId(),
                        Discount::getPercentage,
                        (existing, replacement) -> existing // keeps first matching percentage
                ));

        return cart.getItems().stream()
                .filter(Objects::nonNull)
                .map(item -> calculateItemDiscountWithMap(item, discountMap))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Internal helper to calculate discount using the pre-computed O(1) Map
    private BigDecimal calculateItemDiscountWithMap(CartItem item, Map<Long, BigDecimal> discountMap) {
        if (item == null || item.getProduct() == null || item.getProduct().getCategory() == null) {
            return BigDecimal.ZERO;
        }

        Long categoryId = item.getProduct().getCategory().getId();
        BigDecimal percentage = discountMap.get(categoryId);

        if (percentage == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal basePrice = (item.getUnitPrice() != null) ? item.getUnitPrice() : item.getProduct().getPrice();
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        int qty = (item.getQuantity() != null && item.getQuantity() > 0)
                ? item.getQuantity()
                : CartConstants.DEFAULT_QUANTITY;
        BigDecimal lineSubtotal = basePrice.multiply(BigDecimal.valueOf(qty));

        return lineSubtotal.multiply(percentage)
                .divide(new BigDecimal(CartConstants.PERCENTAGE_DIVISOR), CartConstants.MONEY_SCALE, RoundingMode.HALF_UP);
    }

    // Original method kept for compatibility with OrderProcessingWorker
    public BigDecimal calculateItemDiscount(CartItem item, List<Discount> activeDiscounts) {
        if (item == null || item.getProduct() == null || item.getProduct().getCategory() == null) {
            return BigDecimal.ZERO;
        }

        if (activeDiscounts == null || activeDiscounts.isEmpty()) {
            return BigDecimal.ZERO;
        }

        Long categoryId = item.getProduct().getCategory().getId();
        BigDecimal basePrice = (item.getUnitPrice() != null) ? item.getUnitPrice() : item.getProduct().getPrice();

        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        int qty = (item.getQuantity() != null && item.getQuantity() > 0)
                ? item.getQuantity()
                : CartConstants.DEFAULT_QUANTITY;
        BigDecimal lineSubtotal = basePrice.multiply(BigDecimal.valueOf(qty));

        return activeDiscounts.stream()
                .filter(Discount::getActive)
                .filter(discount -> discount.getCategory() != null)
                .filter(discount -> categoryId.equals(discount.getCategory().getId()))
                .filter(discount -> discount.getPercentage() != null
                        && discount.getPercentage().compareTo(BigDecimal.ZERO) > 0)
                .map(discount -> lineSubtotal.multiply(discount.getPercentage())
                        .divide(new BigDecimal(CartConstants.PERCENTAGE_DIVISOR), CartConstants.MONEY_SCALE, RoundingMode.HALF_UP))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}