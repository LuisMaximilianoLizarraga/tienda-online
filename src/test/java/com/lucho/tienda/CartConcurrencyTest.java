package com.lucho.tienda;

import com.lucho.tienda.dto.ProductOperationRequest;
import com.lucho.tienda.model.Cart;
import com.lucho.tienda.model.CartItem;
import com.lucho.tienda.model.Category;
import com.lucho.tienda.model.Product;
import com.lucho.tienda.model.User;
import com.lucho.tienda.repository.*;
import com.lucho.tienda.service.CartService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class CartConcurrencyTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DiscountRepository discountRepository;

    private Long cartId;
    private final String productCode = "CONCURRENCY-01";

    @BeforeEach
    void setUp() {
        // Clean up the test database in proper Foreign Key order before execution
        cartRepository.deleteAll();
        discountRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();

        // 1. Create test User
        User user = new User();
        user.setUsername("Usuario Test Concurrencia");
        user.setEmail("test-concurrency@test.com");
        user.setPassword("123456");
        user = userRepository.save(user);

        // 2. Create Category and Product
        Category category = new Category();
        category.setName("Categoria Test Concurrencia");
        category = categoryRepository.save(category);

        Product product = new Product();
        product.setCode(productCode);
        product.setName("Producto Concurrente");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(100); // High stock to prevent out-of-stock failures
        product.setCategory(category);
        productRepository.save(product);

        // 3. Create an empty Cart for the user
        Cart cart = cartService.createCart(user.getId());
        cartId = cart.getId();
    }

    @AfterEach
    void tearDown() {
        // Clean up the test database after execution
        cartRepository.deleteAll();
        discountRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void addProduct_ConcurrentRequests_ShouldSucceedThroughRetries() throws InterruptedException {
        int numberOfThreads = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

        // Latches to synchronize worker threads and trigger simultaneous execution
        CountDownLatch readyLatch = new CountDownLatch(numberOfThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numberOfThreads);

        AtomicInteger concurrencyErrors = new AtomicInteger(0);

        ProductOperationRequest request = new ProductOperationRequest(cartId, productCode);

        // Prepare and enqueue 3 parallel threads
        for (int i = 0; i < numberOfThreads; i++) {
            executorService.submit(() -> {
                try {
                    readyLatch.countDown(); // Signal that thread is ready at the starting line
                    startLatch.await();     // Wait for starting signal

                    // Main invocation triggering optimistic locking (@Version) conflicts
                    cartService.addProduct(request);

                } catch (Exception e) {
                    concurrencyErrors.incrementAndGet(); // Log failures if @Retryable mechanism fails
                    e.printStackTrace();
                } finally {
                    doneLatch.countDown(); // Signal that worker task finished
                }
            });
        }

        readyLatch.await();     // Main thread waits until all 3 workers reach the starting line
        startLatch.countDown(); // Simultaneous start trigger for all threads
        doneLatch.await();      // Wait for all threads to complete execution

        // === FINAL VERIFICATIONS ===

        // 1. Verify that @Retryable successfully handled all OptimisticLockingFailureExceptions
        assertEquals(0, concurrencyErrors.get(),
                "All threads should succeed without throwing unhandled exceptions thanks to @Retryable.");

        // 2. Fetch cart via JOIN FETCH (findCartById) to prevent LazyInitializationException
        Cart finalCart = cartRepository.findCartById(cartId).orElseThrow();
        CartItem finalItem = finalCart.getItems().stream()
                .filter(item -> item.getProduct().getCode().equals(productCode))
                .findFirst()
                .orElseThrow();

        assertEquals(3, finalItem.getQuantity(),
                "Cart product quantity must be exactly 3, accurately reflecting every click.");
    }
}