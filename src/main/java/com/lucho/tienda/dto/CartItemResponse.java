package com.lucho.tienda.dto;

import com.lucho.tienda.model.CartItem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record CartItemResponse(
        Long id,
        String productCode,
        Integer quantity,
        BigDecimal unitPrice,        // Original price captured at checkout
        BigDecimal discountAmount,   // Frozen unit discount captured at checkout
        BigDecimal netUnitPrice,     // Real unit price paid (unitPrice - discountAmount)
        BigDecimal lineTotal         // Final row total (netUnitPrice * quantity)
) {
    public static CartItemResponse fromEntity(CartItem item) {
        if (item == null) return null;

        // Uses frozen unitPrice if cart is PROCESSED; falls back to live product price if still CREATED
        BigDecimal listPrice = (item.getUnitPrice() != null)
                ? item.getUnitPrice()
                : item.getProduct().getPrice();

        BigDecimal discount = (item.getDiscountAmount() != null)
                ? item.getDiscountAmount()
                : BigDecimal.ZERO;

        BigDecimal netUnit = listPrice.subtract(discount);
        int qty = (item.getQuantity() != null && item.getQuantity() > 0) ? item.getQuantity() : 1;
        BigDecimal total = netUnit.multiply(BigDecimal.valueOf(qty));

        return new CartItemResponse(
                item.getId(),
                item.getProduct().getCode(),
                qty,
                listPrice,
                discount,
                netUnit,
                total
        );
    }

    // Missing method that caused the compilation error
    public static List<CartItemResponse> fromEntityList(List<CartItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }

        return items.stream()
                .map(CartItemResponse::fromEntity)
                .toList();
    }
}