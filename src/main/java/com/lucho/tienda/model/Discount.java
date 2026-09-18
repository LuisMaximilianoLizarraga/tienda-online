package com.lucho.tienda.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "descuentos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Discount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Category category;

    @Column(name = "porcentaje", nullable = false)
    private BigDecimal percentage = BigDecimal.ZERO;

    @Column(name = "activo", nullable = false)
    private Boolean active = Boolean.FALSE;
}