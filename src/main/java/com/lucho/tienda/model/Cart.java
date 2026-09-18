package com.lucho.tienda.model;

import com.lucho.tienda.model.enums.CartStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "carritos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private User user;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    Set<CartItem> items = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    @Builder.Default
    private CartStatus status = CartStatus.CREATED;

    @Builder.Default
    @Column(name = "monto_total", nullable = false)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Version
    private Long version;

    public BigDecimal calculateTotal() {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO;
        }

        return items.stream()
                .filter(java.util.Objects::nonNull) // Evita NPE si hay elementos null en la lista
                .map(item -> {
                    BigDecimal effectivePrice = BigDecimal.ZERO;

                    // Validación segura encadenada para evitar NullPointerExceptions
                    if (item.getUnitPrice() != null) {
                        effectivePrice = item.getNetUnitPrice() != null ? item.getNetUnitPrice() : BigDecimal.ZERO;
                    } else if (item.getProduct() != null && item.getProduct().getPrice() != null) {
                        effectivePrice = item.getProduct().getPrice();
                    }

                    int qty = (item.getQuantity() != null && item.getQuantity() > 0) ? item.getQuantity() : 1;

                    return effectivePrice.multiply(BigDecimal.valueOf(qty));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}