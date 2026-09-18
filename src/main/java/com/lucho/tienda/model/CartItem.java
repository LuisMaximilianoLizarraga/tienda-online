package com.lucho.tienda.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "carrito_items", indexes = {
        @Index(name = "idx_cart_items_cart_id", columnList = "carrito_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrito_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Product product;

    @Column(name = "cantidad", nullable = false)
    private Integer quantity = 1;

    // Frozen list price per unit captured at checkout time
    @Column(name = "precio_unitario")
    private BigDecimal unitPrice;

    // Frozen discount amount per unit captured at checkout time
    @Column(name = "monto_descuento")
    private BigDecimal discountAmount;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CartItem cartItem)) return false;

        // If already persisted, compare by ID
        if (id != null && cartItem.id != null) {
            return Objects.equals(id, cartItem.id);
        }
        // Otherwise, compare by the associated product
        return product != null && cartItem.product != null &&
                Objects.equals(product.getCode(), cartItem.product.getCode());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // Helper method to compute the net unit price after discount
    public BigDecimal getNetUnitPrice() {
        if (unitPrice == null) return BigDecimal.ZERO;
        BigDecimal discount = (discountAmount != null) ? discountAmount : BigDecimal.ZERO;
        return unitPrice.subtract(discount);
    }

    // Final subtotal charged for this line item
    public BigDecimal getLineTotal() {
        return getNetUnitPrice().multiply(BigDecimal.valueOf(quantity != null ? quantity : 1));
    }
}