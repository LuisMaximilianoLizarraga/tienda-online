package com.lucho.tienda.controller;

import com.lucho.tienda.annotation.ApiStandardErrorResponses;
import com.lucho.tienda.constant.ApiEndpointConstants;
import com.lucho.tienda.constant.ApiFieldConstants;
import com.lucho.tienda.constant.MessageConstants;
import com.lucho.tienda.constant.OpenApiMessageConstants;
import com.lucho.tienda.dto.*;
import com.lucho.tienda.dto.*;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.security.JwtUtils;
import com.lucho.tienda.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static com.lucho.tienda.constant.ApiEndpointConstants.*;
import static com.lucho.tienda.constant.ApiFieldConstants.*;
import static com.lucho.tienda.constant.OpenApiMessageConstants.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping(ApiEndpointConstants.ENDPOINT_CARTS)
@RequiredArgsConstructor
@ApiStandardErrorResponses
@Tag(name = "Cart Controller", description = OpenApiMessageConstants.TAG_CART_DESC)
public class CartController {

    private final CartService cartService;
    private final JwtUtils jwtUtils;

    @Operation(summary = OP_CREATE_CART, description = OP_CREATE_CART_DESC)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = CREATED)
    })
    @PostMapping
    public ResponseEntity<CartResponse> createCart(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long userId = jwtUtils.extractUserIdFromHeader(authHeader);
        Cart cart = cartService.createCart(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(CartResponse.fromEntity(cart));
    }

    @Operation(summary = OP_ADD_PRODUCT)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = OK_PRODUCT),
            @ApiResponse(responseCode = "409", description = CONFLICT)
    })
    @PostMapping(SUB_ENDPOINT_CART_PRODUCTS)
    public ResponseEntity<CartResponse> addProduct(
            @PathVariable(PARAM_CART_ID) Long cartId,
            @RequestBody @Validated AddProductRequest request) {

        ProductOperationRequest serviceRequest = new ProductOperationRequest(cartId, request.productCode());
        Cart cart = cartService.addProduct(serviceRequest);
        return ResponseEntity.ok(CartResponse.fromEntity(cart));
    }

    @Operation(summary = OP_REMOVE_PRODUCT)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = OK_PRODUCT),
            @ApiResponse(responseCode = "409", description = CONFLICT)
    })
    @DeleteMapping(SUB_ENDPOINT_REMOVE_PRODUCT)
    public ResponseEntity<CartResponse> removeProduct(
            @PathVariable(PARAM_CART_ID) Long cartId,
            @PathVariable(FIELD_PRODUCT_CODE) String productCode) {

        ProductOperationRequest request = new ProductOperationRequest(cartId, productCode);
        Cart cart = cartService.removeProduct(request);

        return ResponseEntity.ok(CartResponse.fromEntity(cart));
    }

    @GetMapping(SUB_ENDPOINT_CART_PRODUCTS)
    public ResponseEntity<List<CartItemResponse>> getCartProducts(@PathVariable(PARAM_CART_ID) Long cartId) {
        List<CartItem> items = cartService.getCartProducts(cartId);
        return ResponseEntity.ok(CartItemResponse.fromEntityList(items));
    }

    @Operation(summary = OP_GET_USER_CARTS)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = OK_LIST)
    })
    @GetMapping
    public ResponseEntity<List<CartResponse>> getUserCarts(@RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        Long userId = jwtUtils.extractUserIdFromHeader(authHeader);
        List<Cart> carts = cartService.getUserCarts(userId);
        return ResponseEntity.ok(CartResponse.fromEntityList(carts));
    }

    @Operation(summary = OP_PROCESS_ORDER)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = ACCEPTED),
            @ApiResponse(responseCode = "409", description = CONFLICT)
    })
    @PostMapping(SUB_ENDPOINT_PROCESS_ORDER)
    public ResponseEntity<AsyncOrderResponse> processOrder(@PathVariable(PARAM_CART_ID) Long cartId) {
        cartService.initiateCheckout(cartId);
        AsyncOrderResponse response = new AsyncOrderResponse(MessageConstants.PROCESSING_ORDER);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @Operation(summary = OP_GET_CART)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = OK_CART)
    })
    @GetMapping(SUB_ENDPOINT_GET_CART)
    public ResponseEntity<CartResponse> getCart(@PathVariable(PARAM_CART_ID) Long cartId) {
        Cart cart = cartService.getCartById(cartId);
        return ResponseEntity.ok(CartResponse.fromEntity(cart));
    }

}