# E-Commerce Cart & Processing System

Un backend desarrollado en **Spring Boot** para la gestión resiliente y concurrente de carritos de compras, cálculo optimizado de descuentos y procesamiento asíncrono de órdenes.

---

## 🛠️ Tech Stack

* **Lenguaje:** Java 21
* **Framework:** Spring Boot 3 (Spring Data JPA, Spring Web, Spring Retry)
* **Base de Datos:** H2 (Testing / In-Memory), Hibernate ORM
* **Testing:** JUnit 5, Mockito, AspectJ
* **Utilidades:** Lombok, Java Records (DTOs)

---

## 🚀 Características Principales

* **Ciclo de Vida del Carrito:** Estados definidos por el enum `CartStatus`: `CREATED`, `PROCESSING`, `PROCESSED`, `FAILED`, y `CANCELLED`.
* **Cálculo Optimizado de Descuentos $O(1)$:** `DatabaseDiscountStrategy` indexa los descuentos activos por categoría en un mapa en memoria, evitando iteraciones anidadas $O(N \cdot M)$.
* **Resiliencia ante Concurrencia:** Control de concurrencia optimista con `@Version` combinado con reintentos automáticos `@Retryable` de Spring para absorber excepciones de `OptimisticLockingFailureException` sin perder actualizaciones (*Lost Updates*).
* **Procesamiento Asíncrono de Órdenes:** Descuento seguro de stock con límites transaccionales independientes (`REQUIRES_NEW`) y gestión centralizada de errores vía `CartErrorService`.
* **Congelamiento de Precios:** Preservación de precios unitarios (`unitPrice`) y descuentos (`discountAmount`) al procesar la compra.

---

## 🏗️ Arquitectura y Patrones de Diseño

| Patrón / Mecanismo | Implementación | Propósito |
| --- | --- | --- |
| **Strategy Pattern** | `DatabaseDiscountStrategy` | Desacopla la lógica de cálculo de promociones dinámicas según la categoría del producto. |
| **Optimistic Locking** | Anotación `@Version` en `Cart` | Previene inconsistencias ante escrituras simultáneas en la base de datos. |
| **Retry Pattern** | `@Retryable` en `CartServiceImpl` | Transparencia para el cliente: ante colisión de versiones, el hilo recupera la versión fresca y aplica la operación. |
| **DTO Records** | `CartResponse`, `CartItemResponse` | Exposición inmutable y limpia de entidades hacia la capa REST. |

---

## 🧪 Estrategia de Testing

El proyecto cuenta con un 100% de cobertura de ramas (*branch coverage*) en modelos, DTOs, estrategias y servicios.

### Ejecución de Pruebas

```bash
# Ejecutar toda la suite de pruebas unitarias e integración
./mvnw clean test

```

### Principales Suites de Test

* **`CartConcurrencyTest`:** Simula colisiones extremas en simultáneo (nanosegundos) usando `ExecutorService` y `CountDownLatch`, validando que no existan excepciones sin capturar ni inconsistencias en cantidades.
* **`DatabaseDiscountStrategyTest`:** Cubre todos los escenarios límite de cálculo con mapa indexado y fallback a métodos individuales.
* **`CartItemTest` & DTO Tests:** Validan la aritmética de totales netos, precios congelados e integridad referencial.