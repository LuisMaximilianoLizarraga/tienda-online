package com.lucho.tienda.dto;

public record ProductOperationRequest(
        Long cartId,
        String productCode
) {
}