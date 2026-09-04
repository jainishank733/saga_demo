package com.example.orderservice.saga;

import com.example.orderservice.client.InventoryClient;
import com.example.orderservice.client.PaymentClient;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.entity.Order;
import com.example.orderservice.entity.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

/**
 * Orchestration-based Saga coordinator.
 * <p>
 * This class is the single place that knows the full order of saga steps and their
 * compensations. Each participant service (inventory, payment) only knows how to do
 * or undo its own local step - it has no knowledge of the saga as a whole.
 * <p>
 * Steps:
 *  1. Reserve inventory        (forward)   -> compensation: release inventory
 *  2. Charge payment           (forward)   -> compensation: refund payment
 * <p>
 * If step 2 fails after step 1 succeeded, the orchestrator runs the compensation
 * for step 1 to undo the partial work, keeping the system consistent.
 */
@Service
public class OrderSagaOrchestrator {

    private final OrderRepository orderRepository;
    private final InventoryClient inventoryClient;
    private final PaymentClient paymentClient;

    public OrderSagaOrchestrator(OrderRepository orderRepository,
                                  InventoryClient inventoryClient,
                                  PaymentClient paymentClient) {
        this.orderRepository = orderRepository;
        this.inventoryClient = inventoryClient;
        this.paymentClient = paymentClient;
    }

    public OrderResponse processOrder(OrderRequest request) {
        Order order = new Order(request.getProductCode(), request.getQuantity(),
                request.getAmount(), OrderStatus.PENDING);
        order = orderRepository.save(order);

        // ---- Step 1: reserve inventory ----
        try {
            inventoryClient.reserveStock(order.getId(), request.getProductCode(), request.getQuantity());
        } catch (RestClientException ex) {
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            return new OrderResponse(order.getId(), order.getStatus().name(),
                    "Order failed: inventory reservation rejected - " + ex.getMessage());
        }

        // ---- Step 2: charge payment ----
        try {
            paymentClient.charge(order.getId(), request.getAmount());
        } catch (RestClientException ex) {
            // ---- Compensation: undo step 1 because step 2 failed ----
            try {
                inventoryClient.releaseStock(order.getId(), request.getProductCode(), request.getQuantity());
            } catch (RestClientException compensationEx) {
                order.setStatus(OrderStatus.FAILED);
                orderRepository.save(order);
                return new OrderResponse(order.getId(), order.getStatus().name(),
                        "Order failed AND compensation failed: inventory could not be released ("
                                + compensationEx.getMessage() + "). Manual reconciliation needed.");
            }
            order.setStatus(OrderStatus.ROLLED_BACK);
            orderRepository.save(order);
            return new OrderResponse(order.getId(), order.getStatus().name(),
                    "Order rolled back: payment declined - " + ex.getMessage()
                            + ". Inventory reservation was compensated (released).");
        }

        // ---- All steps succeeded ----
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        return new OrderResponse(order.getId(), order.getStatus().name(), "Order confirmed successfully.");
    }
}
