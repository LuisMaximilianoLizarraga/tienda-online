package com.lucho.tienda.dto;

import com.lucho.tienda.constant.ErrorMessageConstants;
import jakarta.validation.constraints.NotBlank;

public record AddProductRequest(
        @NotBlank(message = ErrorMessageConstants.PRODUCT_CODE_REQUIRED)
        String productCode
) {
}