package com.example.orderservice.client;

import com.example.orderservice.dto.InventoryReserveRequest;
import com.example.orderservice.dto.InventoryReserveResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class InventoryClient {

    private final RestTemplate restTemplate;
    private final String inventoryServiceUrl;

    public InventoryClient(RestTemplate restTemplate,
                            @Value("${services.inventory.url}") String inventoryServiceUrl) {
        this.restTemplate = restTemplate;
        this.inventoryServiceUrl = inventoryServiceUrl;
    }

    /**
     * Saga step: reserve stock for an order.
     * Throws org.springframework.web.client.RestClientException (e.g. HttpClientErrorException
     * for a 409 Conflict) if the inventory service rejects the reservation.
     */
    public InventoryReserveResponse reserveStock(Long orderId, String productCode, Integer quantity) {
        InventoryReserveRequest request = new InventoryReserveRequest(orderId, productCode, quantity);
        return restTemplate.postForObject(
                inventoryServiceUrl + "/api/inventory/reserve",
                request,
                InventoryReserveResponse.class);
    }

    /**
     * Compensating transaction: release previously reserved stock.
     */
    public void releaseStock(Long orderId, String productCode, Integer quantity) {
        InventoryReserveRequest request = new InventoryReserveRequest(orderId, productCode, quantity);
        restTemplate.postForObject(
                inventoryServiceUrl + "/api/inventory/release",
                request,
                InventoryReserveResponse.class);
    }
}
