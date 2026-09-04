package com.example.orderservice.dto;

public class InventoryReserveRequest {

    private Long orderId;
    private String productCode;
    private Integer quantity;

    public InventoryReserveRequest() {
    }

    public InventoryReserveRequest(Long orderId, String productCode, Integer quantity) {
        this.orderId = orderId;
        this.productCode = productCode;
        this.quantity = quantity;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
