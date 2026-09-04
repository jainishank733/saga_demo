package com.example.orderservice.dto;

import java.math.BigDecimal;

public class OrderRequest {

    private String productCode;
    private Integer quantity;
    private BigDecimal amount;

    public OrderRequest() {
    }

    public OrderRequest(String productCode, Integer quantity, BigDecimal amount) {
        this.productCode = productCode;
        this.quantity = quantity;
        this.amount = amount;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }
}
